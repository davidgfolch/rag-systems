package com.rag.provider.services;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.provider.adapter.ProviderClientFactory;
import com.rag.provider.domain.ModelSpec;
import com.rag.provider.domain.ProviderProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the active chat/embedding model specs and their live clients. Reads are
 * lock-free (a single volatile immutable {@link State} snapshot): in-flight
 * generation keeps using the client it started with, and switching just swaps
 * the snapshot. Only switching and profile configuration take the lock, so no
 * request ever waits for a provider round trip.
 *
 * <p>The embedding dimension is detected at most once per active model: a
 * single-flight probe runs on a virtual thread and all concurrent callers await
 * the same result instead of serializing behind the monitor.
 */
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private static final int UNKNOWN_DIMENSION = -1;
    private static final String PROBE_TEXT = "probe";

    private final ProviderRegistry registry;
    private final ProviderClientFactory factory;
    private final Map<String, EmbeddingModel> variantEmbeddingModels = new ConcurrentHashMap<>();
    @SuppressWarnings("java:S3077") // State is an immutable snapshot; the reference is swapped atomically
    private volatile State state;
    @SuppressWarnings("java:S3077") // CompletableFuture is thread-safe; the reference is swapped atomically
    private volatile CompletableFuture<Integer> dimensionProbe;

    public ModelRouter(ProviderRegistry registry, ProviderClientFactory factory,
                       ModelSpec initialChat, ModelSpec initialEmbedding) {
        this.registry = registry;
        this.factory = factory;
        this.state = new State(initialChat, initialEmbedding, null, null, UNKNOWN_DIMENSION);
    }

    public void switchChat(ModelSpec spec) {
        var profile = requireProfile(spec.providerId());
        var model = factory.chatModel(profile, spec.model());
        synchronized (this) {
            var current = state;
            state = new State(spec, current.activeEmbedding(), model,
                    current.embeddingModel(), current.embeddingDimension());
        }
        log.info("Active chat model switched to {}", spec);
    }

    public void switchChat(ModelSpecDTO request) {
        switchChat(new ModelSpec(request.providerId(), request.model()));
    }

    public void switchEmbedding(ModelSpec spec) {
        var profile = requireProfile(spec.providerId());
        var model = factory.embeddingModel(profile, spec.model());
        synchronized (this) {
            var current = state;
            state = new State(current.activeChat(), spec, current.chatModel(), model, UNKNOWN_DIMENSION);
            dimensionProbe = null;
            variantEmbeddingModels.clear();
        }
        log.info("Active embedding model switched to {} (dimension resolved on demand)", spec);
    }

    public void switchEmbedding(ModelSpecDTO request) {
        switchEmbedding(new ModelSpec(request.providerId(), request.model()));
    }

    /**
     * Upserts a profile; active specs pointing at it are rebuilt so the new
     * credentials/base URL take effect immediately.
     */
    public void configure(ConfigureProviderRequest request) {
        var profile = registry.save(request);
        synchronized (this) {
            if (profile.id().equals(state.activeChat().providerId())) {
                switchChat(state.activeChat());
            }
            if (profile.id().equals(state.activeEmbedding().providerId())) {
                switchEmbedding(state.activeEmbedding());
            }
        }
    }

    public ModelSpec currentChat() {
        return state.activeChat();
    }

    public ModelSpec currentEmbedding() {
        return state.activeEmbedding();
    }

    public String provider() {
        return state.activeChat().providerId();
    }

    public ChatModel chatModel() {
        return state.chatModel();
    }

    public EmbeddingModel embeddingModel() {
        return state.embeddingModel();
    }

    /**
     * Embedding client for a pipeline variant's model. A blank model, or the
     * active one, reuses the global client; any other model is built on the
     * active embedding provider and cached, so switching variants never has to
     * change the global active model.
     */
    public EmbeddingModel embeddingModelFor(String model) {
        var current = state;
        if (model == null || model.isBlank() || model.equals(current.activeEmbedding().model())) {
            return current.embeddingModel();
        }
        return variantEmbeddingModels.computeIfAbsent(model,
                variant -> buildEmbeddingModel(variant, current.activeEmbedding()));
    }

    private EmbeddingModel buildEmbeddingModel(String model, ModelSpec activeEmbedding) {
        var profile = requireProfile(activeEmbedding.providerId());
        log.info("Using embedding model {} on provider {}", model, profile.id());
        return factory.embeddingModel(profile, model);
    }

    public int embeddingDimension() {
        var current = state;
        if (current.embeddingDimension() != UNKNOWN_DIMENSION) {
            return current.embeddingDimension();
        }
        var probe = probeFor(current);
        var dimension = probe.join();
        if (dimension > 0) {
            storeDimension(current, dimension);
        } else {
            clearFailedProbe(probe);
        }
        return dimension;
    }

    private CompletableFuture<Integer> probeFor(State current) {
        var probe = dimensionProbe;
        if (probe != null) {
            return probe;
        }
        synchronized (this) {
            if (dimensionProbe == null) {
                var model = current.embeddingModel();
                var started = new CompletableFuture<Integer>();
                Thread.ofVirtual().start(() -> started.complete(detectDimension(model)));
                dimensionProbe = started;
            }
            return dimensionProbe;
        }
    }

    private void storeDimension(State probed, int dimension) {
        synchronized (this) {
            var latest = state;
            if (latest.embeddingModel() == probed.embeddingModel()) {
                state = new State(latest.activeChat(), latest.activeEmbedding(), latest.chatModel(),
                        latest.embeddingModel(), dimension);
            }
        }
    }

    private void clearFailedProbe(CompletableFuture<Integer> probe) {
        synchronized (this) {
            if (dimensionProbe == probe) {
                dimensionProbe = null;
            }
        }
    }

    public ProviderStatusDTO status() {
        var current = state;
        return new ProviderStatusDTO(
                new ModelSpecDTO(current.activeChat().providerId(), current.activeChat().model()),
                new ModelSpecDTO(current.activeEmbedding().providerId(), current.activeEmbedding().model()),
                current.activeChat().providerId(),
                embeddingDimension());
    }

    private ProviderProfile requireProfile(String providerId) {
        var profile = registry.find(providerId);
        if (profile.isEmpty()) {
            var available = registry.all().stream().map(ProviderProfile::id).toList();
            throw new IllegalArgumentException(
                    "Unknown provider: " + providerId + ". Available providers: " + available);
        }
        return profile.get();
    }

    private static int detectDimension(EmbeddingModel model) {
        try {
            var dim = model.dimensions();
            if (dim > 0) {
                return dim;
            }
            return model.embed(PROBE_TEXT).length;
        } catch (Exception e) {
            log.warn("Could not detect embedding dimension: {}", e.getMessage());
            return UNKNOWN_DIMENSION;
        }
    }

    private record State(ModelSpec activeChat, ModelSpec activeEmbedding, ChatModel chatModel,
                         EmbeddingModel embeddingModel, int embeddingDimension) {
    }
}
