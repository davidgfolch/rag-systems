package com.rag.basic.parsing;

import com.rag.common.domain.Document;
import com.rag.common.services.DocumentParser;

/**
 * Default parser that treats document content as already-clean plain text.
 *
 * <p>Used for .txt/.md input where no binary extraction is required.
 */
public class PlainTextParser implements DocumentParser {

    @Override
    public String parse(Document document) {
        return document.getContent();
    }
}