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

/**
 * Holds the active chat/embedding model specs and their live clients. All
 * reads and writes are synchronized, so switching only replaces the references
 * under lock: in-flight generation keeps using the client it started with. The
 * embedding dimension is resolved lazily on first status read so booting does
 * not require a provider round-trip.
 */
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ProviderRegistry registry;
    private final ProviderClientFactory factory;
    private ModelSpec activeChat;
    private ModelSpec activeEmbedding;
    private ChatModel chatModel;
    private EmbeddingModel embeddingModel;
    private int embeddingDimension = -1;

    public ModelRouter(ProviderRegistry registry, ProviderClientFactory factory,
                       ModelSpec initialChat, ModelSpec initialEmbedding) {
        this.registry = registry;
        this.factory = factory;
        this.activeChat = initialChat;
        this.activeEmbedding = initialEmbedding;
    }

    public synchronized void switchChat(ModelSpec spec) {
        var profile = requireProfile(spec.providerId());
        this.chatModel = factory.chatModel(profile, spec.model());
        this.activeChat = spec;
        log.info("Active chat model switched to {}", spec);
    }

    public void switchChat(ModelSpecDTO request) {
        switchChat(new ModelSpec(request.providerId(), request.model()));
    }

    public synchronized void switchEmbedding(ModelSpec spec) {
        var profile = requireProfile(spec.providerId());
        this.embeddingModel = factory.embeddingModel(profile, spec.model());
        this.activeEmbedding = spec;
        this.embeddingDimension = -1;
        log.info("Active embedding model switched to {} (dimension resolved on demand)", spec);
    }

    public void switchEmbedding(ModelSpecDTO request) {
        switchEmbedding(new ModelSpec(request.providerId(), request.model()));
    }

    /**
     * Upserts a profile; active specs pointing at it are rebuilt so the new
     * credentials/base URL take effect immediately.
     */
    public synchronized void configure(ConfigureProviderRequest request) {
        var profile = registry.save(request);
        if (profile.id().equals(activeChat.providerId())) {
            switchChat(activeChat);
        }
        if (profile.id().equals(activeEmbedding.providerId())) {
            switchEmbedding(activeEmbedding);
        }
    }

    public synchronized ModelSpec currentChat() {
        return activeChat;
    }

    public synchronized ModelSpec currentEmbedding() {
        return activeEmbedding;
    }

    public synchronized String provider() {
        return activeChat.providerId();
    }

    public synchronized ChatModel chatModel() {
        return chatModel;
    }

    public synchronized EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    public synchronized int embeddingDimension() {
        if (embeddingDimension < 0) {
            embeddingDimension = detectDimension(embeddingModel);
        }
        return embeddingDimension;
    }

    public synchronized ProviderStatusDTO status() {
        return new ProviderStatusDTO(
                new ModelSpecDTO(activeChat.providerId(), activeChat.model()),
                new ModelSpecDTO(activeEmbedding.providerId(), activeEmbedding.model()),
                provider(), embeddingDimension());
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
            return model.embed("probe").length;
        } catch (Exception e) {
            log.warn("Could not detect embedding dimension: {}", e.getMessage());
            return -1;
        }
    }
}