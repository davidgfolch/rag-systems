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
        var records = new RecordAccumulator();
        for (var block : document.blocks()) {
            if (block.isBlank()) {
                continue;
            }
            if (isHeading(block)) {
                records.flush(groups);
            } else {
                if (records.shouldFlush(block, config, maxChars)) {
                    records.flush(groups);
                }
                records.add(block);
            }
        }
        records.flush(groups);
        log.debug("Grouped {} JSON blocks into {} record groups", document.blocks().size(), groups.size());
        return groups;
    }

    private static boolean isHeading(DocumentBlock block) {
        return block.type() == BlockType.HEADING || block.type() == BlockType.TITLE;
    }

    private static final class RecordAccumulator {
        private final List<DocumentBlock> records = new ArrayList<>();
        private int chars;

        void add(DocumentBlock block) {
            if (!records.isEmpty()) chars += BLOCK_SEPARATOR.length();
            chars += block.content().length();
            records.add(block);
        }

        boolean shouldFlush(DocumentBlock block, ChunkingConfig config, int maxChars) {
            if (records.isEmpty()) {
                return false;
            }
            if (records.size() >= config.maxRecordsPerChunk()) {
                return true;
            }
            if (!records.getFirst().sectionPath().equals(block.sectionPath())) {
                return true;
            }
            return chars + block.content().length() > maxChars;
        }

        void flush(List<BlockGroup> groups) {
            if (records.isEmpty()) {
                return;
            }
            var first = records.getFirst();
            groups.add(new BlockGroup(join(), contentType(), first.sectionPath(),
                    first.location(), first.metadata(), false, context(first.sectionPath())));
            records.clear();
            chars = 0;
        }

        private String join() {
            return String.join(BLOCK_SEPARATOR, records.stream().map(DocumentBlock::content).toList());
        }

        private static String context(List<String> section) {
            return String.join(CONTEXT_SEPARATOR, section);
        }

        private BlockType contentType() {
            if (records.stream().allMatch(block -> block.type() == BlockType.JSON_ARRAY)) {
                return BlockType.JSON_ARRAY;
            }
            return BlockType.JSON_OBJECT;
        }
    }
}
