package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Tika-based parser for binary formats (PDF, DOCX, XLSX, PPTX, RTF, ...).
 *
 * <p>Extracts textual content from the document's raw bytes, carried either as
 * a {@code byte[]} ({@code rawBytes} metadata, from multipart upload) or as a
 * base64 string ({@code raw} metadata) for backward compatibility.
 */
public class TikaDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentParser.class);

    private final SourceType sourceType;
    private final TikaXhtmlExtractor extractor;

    public TikaDocumentParser() {
        this.sourceType = SourceType.UNKNOWN;
        this.extractor = new TikaXhtmlExtractor();
    }

    @Override
    public ParsedDocument parse(Document document) {
        var rawBytes = extractor.rawBytes(document);
        var text = rawBytes.length == 0
                ? document.getContent() : Jsoup.parse(extractor.extract(document, rawBytes)).text();
        log.debug("Parsed document {} ({} raw bytes -> {} chars)",
                document.getId(), rawBytes.length, text.length());
        var block = new DocumentBlock(BlockType.PARAGRAPH, text);
        return new ParsedDocument(document.getId(), sourceType,
                ParsedMetadata.withFormat(document, sourceType), List.of(block));
    }
}
