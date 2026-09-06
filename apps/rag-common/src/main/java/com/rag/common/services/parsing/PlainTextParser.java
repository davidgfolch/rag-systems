package com.rag.common.services.parsing;

import com.rag.common.domain.Document;
import com.rag.common.services.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default parser that treats document content as already-clean plain text.
 *
 * <p>Used for .txt/.md input where no binary extraction is required.
 */
public class PlainTextParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PlainTextParser.class);

    @Override
    public String parse(Document doc) {
        log.debug("Plain-text passthrough for document {} ({} chars)", doc.getId(),
                doc.getContent() == null ? 0 : doc.getContent().length());
        return doc.getContent();
    }
}