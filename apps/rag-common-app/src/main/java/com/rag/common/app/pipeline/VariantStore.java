package com.rag.common.app.pipeline;

import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.TextSplitter;
import com.rag.common.generation.adapter.SpringAiEmbeddingModel;
import com.rag.contract.pipeline.ChunkingStrategy;
import com.rag.contract.pipeline.PipelineVariantDTO;
import org.slf4j.Logger;
import org.springframework.ai.embedding.EmbeddingModel;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Per-module registry of runnable pipeline variants and the one currently
 * active. The module's {@link TextSplitter} and {@link VectorStorePort} beans
 * delegate here, so activating a variant changes what the next ingestion and
 * query use without restarting the module.
 *
 * <p>The boot variant seeded from configuration keeps using the module's
 * existing store (its {@code chunks} table); every other variant owns the
 * {@code variant_<id>} store built by {@link VariantStoreFactory}. Removing a
 * variant drops that table again. When a {@link VariantRepository} is present
 * the registry is also persisted, so variants survive a restart.
 */
public class VariantStore {

    private static final Logger log = LoggerFactory.getLogger(VariantStore.class);

    private static final String ID_PREFIX = "v";
    private static final Pattern VARIANT_ID = Pattern.compile("[A-Za-z0-9_]{1,60}");

    private final String module;
    private final TextSplitter defaultSplitter;
    private final VariantStores storage;
    private final VectorStorePort defaultStore;
    private final VariantRepository repository;
    private final Map<String, PipelineVariantDTO> variants = new ConcurrentHashMap<>();
    private final Map<String, TextSplitter> splitters = new ConcurrentHashMap<>();
    private final Map<String, VectorStorePort> stores = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddings = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    private volatile String activeId;
    private volatile String defaultVariantId;

    public VariantStore(String module, TextSplitter defaultSplitter, VariantStores storage,
                        VectorStorePort defaultStore) {
        this(module, defaultSplitter, storage, defaultStore, null);
    }

    public VariantStore(String module, TextSplitter defaultSplitter, VariantStores storage,
                        VectorStorePort defaultStore, VariantRepository repository) {
        this.module = module;
        this.defaultSplitter = defaultSplitter;
        this.storage = storage;
        this.defaultStore = defaultStore;
        this.repository = repository;
    }

    public String module() {
        return module;
    }

    public List<PipelineVariantDTO> variants() {
        return variants.values().stream()
                .sorted(Comparator.comparing(PipelineVariantDTO::variantId))
                .toList();
    }

    public Optional<PipelineVariantDTO> find(String variantId) {
        return variantId == null ? Optional.empty() : Optional.ofNullable(variants.get(variantId));
    }

    public Optional<PipelineVariantDTO> active() {
        return find(activeId);
    }

    public PipelineVariantDTO add(PipelineVariantDTO request) {
        return add(ID_PREFIX + ids.incrementAndGet(), request);
    }

    public PipelineVariantDTO add(String variantId, PipelineVariantDTO request) {
        requireValidId(variantId);
        requireValidWindow(request);
        var variant = withIdentity(variantId, request);
        variants.put(variantId, variant);
        activateIfNone();
        sync();
        log.info("Variant registered on {}: {} ({} {}/{})", module, variantId, variant.strategy(),
                variant.chunkSize(), variant.chunkOverlap());
        return variant;
    }

    /** Registers the configuration-derived variant and makes it the active, default storage one. */
    public PipelineVariantDTO seed(String variantId, PipelineVariantDTO request) {
        var variant = add(variantId, request);
        defaultVariantId = variantId;
        activeId = variantId;
        sync();
        return variant;
    }

    /**
     * Replaces the in-memory registry with the persisted one. A module that has
     * no PostgreSQL, or a first run with an empty table, keeps the seeded boot
     * variant and simply persists it.
     */
    public void restore() {
        if (repository == null) {
            return;
        }
        try {
            repository.ensureTable();
            var persisted = repository.load();
            persisted.forEach(row -> variants.put(row.variant().variantId(),
                    withIdentity(row.variant().variantId(), row.variant())));
            persisted.stream().filter(VariantRepository.PersistedVariant::boot).findFirst()
                    .ifPresent(row -> defaultVariantId = row.variant().variantId());
            persisted.stream().filter(VariantRepository.PersistedVariant::active).findFirst()
                    .ifPresent(row -> activeId = row.variant().variantId());
            ids.set(maxNumericId(persisted));
            activateIfNone();
            sync();
            log.info("Restored {} pipeline variant(s) on {} (schema {})",
                    variants.size(), module, repository.schemaName());
        } catch (RuntimeException e) {
            log.warn("Pipeline variants stay in memory on {}: {}", module, e.getMessage());
        }
    }

    public PipelineVariantDTO update(String variantId, PipelineVariantDTO request) {
        var previous = requireExisting(variantId);
        requireValidWindow(request);
        var variant = withIdentity(variantId, request);
        variants.put(variantId, variant);
        splitters.remove(variantId);
        embeddings.remove(variantId);
        if (previous.embeddingDimension() != variant.embeddingDimension()
                || !previous.embeddingModel().equals(variant.embeddingModel())) {
            stores.remove(variantId);
        }
        sync();
        return variant;
    }

    public boolean remove(String variantId) {
        var removed = variants.remove(variantId);
        if (removed == null) {
            return false;
        }
        splitters.remove(variantId);
        stores.remove(variantId);
        embeddings.remove(variantId);
        if (variantId.equals(activeId)) {
            activeId = variants.keySet().stream().sorted().findFirst().orElse(null);
        }
        dropStorage(removed);
        sync();
        return true;
    }

    public PipelineVariantDTO activate(String variantId) {
        var variant = requireExisting(variantId);
        activeId = variantId;
        sync();
        log.info("Variant activated on {}: {}", module, variantId);
        return variant;
    }

    public TextSplitter splitter() {
        return active()
                .map(variant -> splitters.computeIfAbsent(variant.variantId(), _ -> chunkerFor(variant)))
                .orElse(defaultSplitter);
    }

    /**
     * Embedding model of the active variant, so ingestion and retrieval embed
     * with the same model the variant's table was built for.
     */
    public EmbeddingModel embeddingModel() {
        return active()
                .map(this::embeddingModelOf)
                .orElseThrow(() -> new IllegalStateException("No active pipeline variant on " + module));
    }

    public VectorStorePort store() {
        var current = active();
        if (current.isEmpty() || current.get().variantId().equals(defaultVariantId)) {
            return defaultStore;
        }
        var variant = current.get();
        return stores.computeIfAbsent(variant.variantId(), _ -> storage.create(variant));
    }

    /** The semantic chunker cuts with the variant's own model, so boundaries and vectors agree. */
    private TextSplitter chunkerFor(PipelineVariantDTO variant) {
        var strategy = ChunkingStrategy.from(variant.strategy()).orElse(ChunkingStrategy.RECURSIVE);
        return Chunkers.of(strategy, variant.chunkSize(), variant.chunkOverlap(),
                () -> new SpringAiEmbeddingModel(embeddingModelOf(variant)));
    }

    private EmbeddingModel embeddingModelOf(PipelineVariantDTO variant) {
        return embeddings.computeIfAbsent(variant.variantId(), _ -> storage.embedding(variant));
    }

    private PipelineVariantDTO withIdentity(String variantId, PipelineVariantDTO request) {
        return new PipelineVariantDTO(variantId, module, request.strategy(), request.embeddingModel(),
                request.embeddingDimension(), request.chunkSize(), request.chunkOverlap());
    }

    private PipelineVariantDTO requireExisting(String variantId) {
        return find(variantId).orElseThrow(() -> new IllegalArgumentException("Unknown variant: " + variantId));
    }

    /** The boot variant's table is the module's shared {@code chunks}, so it is never dropped. */
    private void dropStorage(PipelineVariantDTO variant) {
        if (variant.variantId().equals(defaultVariantId)) {
            return;
        }
        try {
            storage.drop(variant);
        } catch (RuntimeException e) {
            log.warn("Could not drop storage of {}: {}", variant.variantId(), e.getMessage());
        }
    }

    private void sync() {
        if (repository == null) {
            return;
        }
        try {
            repository.replaceAll(variants(), activeId, defaultVariantId);
        } catch (RuntimeException e) {
            log.warn("Could not persist pipeline variants on {}: {}", module, e.getMessage());
        }
    }

    private static long maxNumericId(List<VariantRepository.PersistedVariant> persisted) {
        return persisted.stream()
                .map(row -> row.variant().variantId())
                .filter(id -> id.startsWith(ID_PREFIX))
                .map(id -> id.substring(ID_PREFIX.length()))
                .filter(suffix -> !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit))
                .mapToLong(Long::parseLong)
                .max()
                .orElse(0);
    }

    private static void requireValidId(String variantId) {
        if (variantId == null || !VARIANT_ID.matcher(variantId).matches()) {
            throw new IllegalArgumentException("Invalid variant id: " + variantId);
        }
    }

    private static void requireValidWindow(PipelineVariantDTO request) {
        var strategy = ChunkingStrategy.from(request.strategy());
        if (strategy.isEmpty()) {
            throw new IllegalArgumentException("Unknown chunking strategy: " + request.strategy());
        }
        if (request.chunkSize() <= 0 || request.chunkOverlap() < 0
                || request.chunkOverlap() >= request.chunkSize()) {
            throw new IllegalArgumentException("Chunk window must satisfy size > 0 and 0 <= overlap < size");
        }
        if (strategy.get() == ChunkingStrategy.TOKEN
                && request.chunkSize() / Chunkers.CHARS_PER_TOKEN
                <= request.chunkOverlap() / Chunkers.CHARS_PER_TOKEN) {
            throw new IllegalArgumentException("Token variants need size / 4 > overlap / 4");
        }
    }

    private void activateIfNone() {
        if (activeId == null) {
            activeId = variants.keySet().stream().sorted().findFirst().orElse(null);
        }
    }
}
