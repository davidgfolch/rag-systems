package com.rag.common.ingestion.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;
import com.rag.common.core.domain.SourceType;
import com.rag.contract.model.PageBlockDTO;
import com.rag.contract.model.PageDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.SOURCE;
import static com.rag.common.core.domain.MetadataKeys.SOURCE_TYPE;
import static com.rag.common.core.domain.MetadataKeys.TITLE;

/**
 * Converts a fetched web page into a structured document, preserving the block
 * order, heading hierarchy and DOM provenance that the fetcher already know.
 * Re-parsing the page text would lose that structure, so it is never done.
 */
public final class PageDocumentMapper {

    private static final Logger log = LoggerFactory.getLogger(PageDocumentMapper.class);

    private static final String WEB = "web";

    private PageDocumentMapper() {
    }

    public static ParsedDocument toParsedDocument(String documentId, PageDTO page) {
        var blocks = blocks(page);
        log.debug("Mapped web page {} into {} blocks", page.getUrl(), blocks.size());
        return new ParsedDocument(documentId, SourceType.HTML, metadata(page), blocks);
    }

    private static Map<String, Object> metadata(PageDTO page) {
        var metadata = new HashMap<String, Object>();
        metadata.put(SOURCE_TYPE, WEB);
        metadata.put(SOURCE, page.getUrl());
        metadata.put(FORMAT, SourceType.HTML.name());
        Optional.ofNullable(page.getTitle())
                .filter(title -> !title.isBlank())
                .ifPresent(title -> metadata.put(TITLE, title));
        return metadata;
    }

    private static List<DocumentBlock> blocks(PageDTO page) {
        var source = page.getBlocks();
        if (source == null || source.isEmpty()) {
            return List.of(new DocumentBlock(BlockType.PARAGRAPH, page.getText()));
        }
        var blocks = new ArrayList<DocumentBlock>(source.size());
        for (var block : source) {
            blocks.add(convert(block));
        }
        return blocks;
    }

    private static DocumentBlock convert(PageBlockDTO block) {
        return new DocumentBlock(blockType(block.getType()), block.getText(),
                block.getSectionPath() == null ? List.of() : List.copyOf(block.getSectionPath()),
                new SourceLocation(null, block.getDomPath(), null, null, null), Map.of());
    }

    private static BlockType blockType(String type) {
        if (type == null) {
            return BlockType.PARAGRAPH;
        }
        try {
            return BlockType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return BlockType.PARAGRAPH;
        }
    }
}
