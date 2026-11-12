package com.rag.common.ingestion.chunking;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;

/**
 * Shared chunk assembly for the text splitters: it scrubs the raw bytes from the
 * document metadata, applies each segment's strategy-specific metadata, numbers
 * the surviving chunks contiguously and creates the {@link Chunk} instances.
 */
final class ChunkerSupport {

    private static final Logger log = LoggerFactory.getLogger(ChunkerSupport.class);

    private ChunkerSupport() {}

    /** One candidate chunk: its text plus the strategy-specific metadata it carries. */
    record Draft(String text, Map<String, Object> metadata) {}

    /** Document metadata without the raw payload, which must never reach the model. */
    static Map<String, Object> documentMeta(Document document) {
        var meta = new HashMap<>(document.getMetadata());
        meta.remove(RAW_BYTES);
        return meta;
    }

    /** Builds chunks from drafts, dropping blank drafts and numbering the survivors contiguously. */
    static List<Chunk> chunks(Document document, Map<String, Object> baseMeta, List<Draft> drafts) {
        var kept = drafts.stream()
                .map(draft -> new Draft(draft.text() == null ? "" : draft.text().trim(), draft.metadata()))
                .filter(draft -> !draft.text().isEmpty())
                .toList();
        if (kept.size() < drafts.size()) {
            log.debug("Dropped {} blank chunk draft(s) for document {}",
                    drafts.size() - kept.size(), document.getId());
        }
        return IntStream.range(0, kept.size())
                .mapToObj(index -> toChunk(document, baseMeta, kept.get(index), index))
                .toList();
    }

    private static Chunk toChunk(Document document, Map<String, Object> baseMeta, Draft draft, int index) {
        var meta = new HashMap<>(baseMeta);
        meta.putAll(draft.metadata());
        return new Chunk(UUID.randomUUID().toString(), document.getId(), draft.text(), index, Map.copyOf(meta));
    }
}
