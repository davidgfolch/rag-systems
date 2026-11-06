package com.rag.common.ingestion.chunking.structural;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_TYPE;
import static com.rag.common.core.domain.MetadataKeys.DOM_PATH;
import static com.rag.common.core.domain.MetadataKeys.END_OFFSET;
import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.JSON_PATH;
import static com.rag.common.core.domain.MetadataKeys.PAGE;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.core.domain.MetadataKeys.SECTION_PATH;
import static com.rag.common.core.domain.MetadataKeys.START_OFFSET;

/**
 * Merges the splitter-produced chunk with the structural provenance of its
 * block group, keeping every metadata value flat and non-null so downstream
 * filters and vector stores can index it directly.
 */
final class StructuralChunkAssembler {

    private static final Logger log = LoggerFactory.getLogger(StructuralChunkAssembler.class);
    private static final String SECTION_SEPARATOR = " > ";

    private StructuralChunkAssembler() {
    }

    static Chunk assemble(Chunk source, ParsedDocument document, BlockGroup group, int index) {
        var metadata = new HashMap<String, Object>();
        metadata.putAll(group.metadata());
        metadata.putAll(source.getMetadata());
        metadata.put(FORMAT, document.sourceType().name());
        metadata.put(CONTENT_TYPE, group.contentType().name());
        putIfPresent(metadata, SECTION_PATH, sectionPath(group.sectionPath()));
        addLocation(metadata, group.location());
        metadata.remove(RAW_BYTES);
        log.debug("Assembled chunk {} with section path {}", index, metadata.get(SECTION_PATH));
        return new Chunk(source.getId(), source.getDocumentId(), source.getContent(), index, metadata);
    }

    private static void addLocation(Map<String, Object> metadata, SourceLocation location) {
        putIfPresent(metadata, PAGE, location.page());
        putIfPresent(metadata, DOM_PATH, location.domPath());
        putIfPresent(metadata, JSON_PATH, location.jsonPath());
        putIfPresent(metadata, START_OFFSET, location.startOffset());
        putIfPresent(metadata, END_OFFSET, location.endOffset());
    }

    private static String sectionPath(List<String> path) {
        return path.isEmpty() ? null : String.join(SECTION_SEPARATOR, path);
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
