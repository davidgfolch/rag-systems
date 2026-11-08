package com.rag.common.ingestion.chunking.structural;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.core.services.DocumentChunker;
import com.rag.common.core.services.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Structural chunking base: groups ordered blocks, then reuses the variant's
 * existing splitting algorithm inside each group, so format awareness layers
 * on top of the established chunkers instead of duplicating them.
 */
public class StructuralDocumentChunker implements DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(StructuralDocumentChunker.class);

    private static final String CONTEXT_SEPARATOR = "\n\n";

    private final Set<SourceType> sourceTypes;
    private final TextSplitter splitter;
    private final BlockGrouper grouper;
    private final int variantMaxChars;

    public StructuralDocumentChunker(SourceType sourceType, TextSplitter splitter,
                                     BlockGrouper grouper, int variantMaxChars) {
        this(Set.of(sourceType), splitter, grouper, variantMaxChars);
    }

    public StructuralDocumentChunker(Set<SourceType> sourceTypes, TextSplitter splitter,
                                     BlockGrouper grouper, int variantMaxChars) {
        if (variantMaxChars <= 0) {
            throw new IllegalArgumentException("variantMaxChars must be > 0");
        }
        this.sourceTypes = Set.copyOf(sourceTypes);
        this.splitter = Objects.requireNonNull(splitter, "splitter must not be null");
        this.grouper = grouper == null ? new SectionAwareBlockGrouper() : grouper;
        this.variantMaxChars = variantMaxChars;
    }

    @Override
    public boolean supports(ParsedDocument document) {
        return sourceTypes.contains(document.sourceType());
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document, ChunkingConfig config) {
        if (document.isBlank()) {
            return List.of();
        }
        var effective = config == null ? ChunkingConfig.DEFAULT : config;
        var groups = grouper.group(document, effective, effective.effectiveMaxChars(variantMaxChars));
        var chunks = new ArrayList<Chunk>();
        int index = 0;
        for (var group : groups) {
            for (var chunk : splitGroup(document, group)) {
                chunks.add(StructuralChunkAssembler.assemble(chunk, document, group, index++));
            }
        }
        log.info("Chunked {} document {} into {} chunks from {} block groups",
                sourceTypes, document.documentId(), chunks.size(), groups.size());
        return chunks;
    }

    private List<Chunk> splitGroup(ParsedDocument document, BlockGroup group) {
        if (group.text().isBlank()) {
            return List.of();
        }
        var text = new Document(document.documentId(), group.text(), document.metadata());
        var pieces = splitter.split(text).stream()
                .filter(piece -> piece.getContent() != null && !piece.getContent().isBlank())
                .toList();
        if (group.context().isBlank()) {
            return pieces;
        }
        return pieces.stream()
                .map(piece -> withContext(piece, group.context()))
                .toList();
    }

    /** Adds the section path to a body piece, so every split keeps its heading context. */
    private static Chunk withContext(Chunk piece, String context) {
        return new Chunk(piece.getId(), piece.getDocumentId(),
                context + CONTEXT_SEPARATOR + piece.getContent().trim(), piece.getIndex(), piece.getMetadata());
    }
}
