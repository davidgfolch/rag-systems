package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Default parser that treats document content as already-clean plain text.
 *
 * <p>Used for .txt/.md input where no binary extraction is required.
 */
public class PlainTextParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PlainTextParser.class);

    @Override
    public ParsedDocument parse(Document doc) {
        log.debug("Plain-text passthrough for document {} ({} chars)", doc.getId(), contentLength(doc));
        var block = new DocumentBlock(BlockType.PARAGRAPH, doc.getContent());
        return new ParsedDocument(doc.getId(), SourceType.TEXT,
                ParsedMetadata.withFormat(doc, SourceType.TEXT), List.of(block));
    }

    private static int contentLength(Document doc) {
        return doc.getContent() == null ? 0 : doc.getContent().length();
    }
}
