package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;

/**
 * Selects the parser that understands the document's format. A missing parser
 * is a legitimate fallback; a parser failure is never swallowed.
 */
public class FormatAwareDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(FormatAwareDocumentParser.class);
    private static final Set<SourceType> TEXTUAL = Set.of(
            SourceType.TEXT, SourceType.HTML, SourceType.MARKDOWN, SourceType.JSON);

    private final Map<SourceType, DocumentParser> parsers;
    private final DocumentParser fallback;

    public FormatAwareDocumentParser(Map<SourceType, DocumentParser> parsers, DocumentParser fallback) {
        this.parsers = Map.copyOf(parsers);
        this.fallback = fallback;
    }

    @Override
    public ParsedDocument parse(Document document) {
        var sourceType = DocumentFormatDetector.detect(document);
        var effective = withTextContent(document, sourceType);
        var parser = parsers.get(sourceType);
        if (parser == null) {
            log.debug("No parser registered for {}; falling back for document {}", sourceType, document.getId());
            return fallback.parse(effective);
        }
        return parser.parse(effective);
    }

    /**
     * Text formats can arrive as raw bytes (file uploads) with empty content.
     * Binary formats keep their bytes for Tika, which decodes them itself.
     */
    private static Document withTextContent(Document document, SourceType sourceType) {
        if (!TEXTUAL.contains(sourceType) || !document.getContent().isBlank()) {
            return document;
        }
        if (document.getMetadata().get(RAW_BYTES) instanceof byte[] bytes && bytes.length > 0) {
            return new Document(document.getId(), new String(bytes, StandardCharsets.UTF_8),
                    document.getMetadata());
        }
        return document;
    }
}
