package com.rag.common.ingestion.parsing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.SourceType;

import java.util.HashMap;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.FORMAT;

/** Shared metadata assembly for parsed documents. */
final class ParsedMetadata {

    private static final Logger log = LoggerFactory.getLogger(ParsedMetadata.class);

    private ParsedMetadata() {
    }

    static Map<String, Object> withFormat(Document document, SourceType sourceType) {
        var metadata = new HashMap<>(document.getMetadata());
        metadata.put(FORMAT, sourceType.name());
        log.debug("Tagged document {} as {}", document.getId(), sourceType);
        return metadata;
    }
}
