package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HTML parser backed by jsoup, keeping headings, lists, tables and page paths. */
public class HtmlDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(HtmlDocumentParser.class);

    private final XhtmlBlockExtractor extractor = new XhtmlBlockExtractor(false);

    @Override
    public ParsedDocument parse(Document document) {
        var parsed = Jsoup.parse(document.getContent());
        var blocks = new ArrayList<>(extractor.extract(parsed));
        addTitle(parsed.title(), blocks);
        log.debug("Parsed HTML document {} into {} blocks", document.getId(), blocks.size());
        return new ParsedDocument(document.getId(), SourceType.HTML,
                ParsedMetadata.withFormat(document, SourceType.HTML), blocks);
    }

    private static void addTitle(String title, List<DocumentBlock> blocks) {
        if (title != null && !title.isBlank()) {
            blocks.addFirst(new DocumentBlock(BlockType.TITLE, title.trim(), List.of(),
                    SourceLocation.NONE, Map.of()));
        }
    }
}
