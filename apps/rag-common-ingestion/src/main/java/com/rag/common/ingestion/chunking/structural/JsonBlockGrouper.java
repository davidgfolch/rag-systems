package com.rag.common.ingestion.chunking.structural;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.services.ChunkingConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups sibling JSON records, never mixing different JSON paths and never
 * exceeding the configured record count or character window.
 */
public class JsonBlockGrouper implements BlockGrouper {

    private static final Logger log = LoggerFactory.getLogger(JsonBlockGrouper.class);

    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final String CONTEXT_SEPARATOR = " > ";

    @Override
    public List<BlockGroup> group(ParsedDocument document, ChunkingConfig config, int maxChars) {
        var groups = new ArrayList<BlockGroup>();
        var records = new ArrayList<DocumentBlock>();
        for (var block : document.blocks()) {
            if (block.isBlank()) {
                continue;
            }
            if (isHeading(block)) {
                flush(records, groups);
            } else {
                if (needsFlush(records, block, config, maxChars)) {
                    flush(records, groups);
                }
                records.add(block);
            }
        }
        flush(records, groups);
        log.debug("Grouped {} JSON blocks into {} record groups", document.blocks().size(), groups.size());
        return groups;
    }

    private static boolean isHeading(DocumentBlock block) {
        return block.type() == BlockType.HEADING || block.type() == BlockType.TITLE;
    }

    private static boolean needsFlush(List<DocumentBlock> records, DocumentBlock block,
                                      ChunkingConfig config, int maxChars) {
        if (records.isEmpty()) {
            return false;
        }
        if (records.size() >= config.maxRecordsPerChunk()) {
            return true;
        }
        if (!records.getFirst().sectionPath().equals(block.sectionPath())) {
            return true;
        }
        return join(records).length() + block.content().length() > maxChars;
    }

    private static void flush(List<DocumentBlock> records, List<BlockGroup> groups) {
        if (records.isEmpty()) {
            return;
        }
        var first = records.getFirst();
        groups.add(new BlockGroup(join(records), contentType(records), first.sectionPath(),
                first.location(), first.metadata(), false, context(first.sectionPath())));
        records.clear();
    }

    private static String join(List<DocumentBlock> records) {
        return String.join(BLOCK_SEPARATOR, records.stream().map(DocumentBlock::content).toList());
    }

    private static String context(List<String> section) {
        return String.join(CONTEXT_SEPARATOR, section);
    }

    private static BlockType contentType(List<DocumentBlock> records) {
        if (records.stream().allMatch(block -> block.type() == BlockType.JSON_ARRAY)) {
            return BlockType.JSON_ARRAY;
        }
        return BlockType.JSON_OBJECT;
    }
}
