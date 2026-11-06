package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Tika-backed parser for XHTML-visible binary formats. The XHTML is kept and
 * walked structurally instead of being flattened to plain text.
 */
public class TikaXhtmlDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TikaXhtmlDocumentParser.class);

    private final SourceType sourceType;
    private final TikaXhtmlExtractor extractor;
    private final XhtmlBlockExtractor blockExtractor;

    public TikaXhtmlDocumentParser(SourceType sourceType, TikaXhtmlExtractor extractor,
                                   boolean heuristicHeadings) {
        this.sourceType = sourceType;
        this.extractor = extractor;
        this.blockExtractor = new XhtmlBlockExtractor(heuristicHeadings);
    }

    @Override
    public ParsedDocument parse(Document document) {
        var rawBytes = extractor.rawBytes(document);
        var blocks = rawBytes.length == 0
                ? plain(document) : extract(document, rawBytes);
        log.debug("Parsed {} document {} into {} blocks", sourceType, document.getId(), blocks.size());
        return new ParsedDocument(document.getId(), sourceType,
                ParsedMetadata.withFormat(document, sourceType), blocks);
    }

    private List<DocumentBlock> extract(Document document, byte[] rawBytes) {
        var xhtml = extractor.extract(document, rawBytes);
        return blockExtractor.extract(Jsoup.parse(xhtml, "", Parser.xmlParser()));
    }

    private List<DocumentBlock> plain(Document document) {
        return List.of(new DocumentBlock(BlockType.PARAGRAPH, document.getContent(),
                List.of(), SourceLocation.NONE, Map.of()));
    }
}
