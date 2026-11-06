package com.rag.common.ingestion.chunking.structural;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.services.ChunkingConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Groups blocks within the same section, flushing at section changes and page
 * boundaries. Tables and code blocks stay in their own protected group.
 */
public class SectionAwareBlockGrouper implements BlockGrouper {

    private static final Logger log = LoggerFactory.getLogger(SectionAwareBlockGrouper.class);

    private static final String CONTEXT_SEPARATOR = " > ";
    private static final String BLOCK_SEPARATOR = "\n\n";

    @Override
    public List<BlockGroup> group(ParsedDocument document, ChunkingConfig config, int maxChars) {
        var groups = new ArrayList<BlockGroup>();
        var accumulator = new GroupAccumulator(maxChars);
        List<String> section = List.of();
        for (var block : document.blocks()) {
            if (block.isBlank()) {
                continue;
            }
            if (isHeading(block)) {
                accumulator.flush(groups);
                section = sectionOf(block);
            } else {
                section = groupBlock(block, config, accumulator, groups, section);
            }
        }
        accumulator.flush(groups);
        log.debug("Grouped {} blocks into {} sections", document.blocks().size(), groups.size());
        return groups;
    }

    private static List<String> groupBlock(DocumentBlock block, ChunkingConfig config,
                                           GroupAccumulator accumulator, List<BlockGroup> groups,
                                           List<String> section) {
        var current = config.respectSectionBoundaries()
                ? section : aggregate(section, block.sectionPath());
        if (sectionChanged(accumulator.section, block)) {
            accumulator.flush(groups);
        }
        if (accumulator.pageChanged(block)) {
            accumulator.flush(groups);
        }
        if (isProtected(block, config)) {
            accumulator.flush(groups);
            groups.add(protectedGroup(block, current));
        } else {
            accumulator.add(block, current);
            if (accumulator.overLimit()) {
                accumulator.flush(groups);
            }
        }
        return current;
    }

    private static boolean isHeading(DocumentBlock block) {
        return block.type() == BlockType.HEADING || block.type() == BlockType.TITLE;
    }

    private static List<String> sectionOf(DocumentBlock block) {
        if (!block.sectionPath().isEmpty()) return block.sectionPath();
        return List.of(block.content());
    }

    private static List<String> aggregate(List<String> current, List<String> next) {
        return next == null || next.isEmpty() ? current : next;
    }

    private static boolean sectionChanged(List<String> current, DocumentBlock block) {
        if (block.sectionPath().isEmpty()) return false;
        return !current.equals(block.sectionPath());
    }

    private static boolean isProtected(DocumentBlock block, ChunkingConfig config) {
        return (block.type() == BlockType.TABLE && config.preserveTables())
                || (block.type() == BlockType.CODE && config.preserveCode());
    }

    private static BlockGroup protectedGroup(DocumentBlock block, List<String> section) {
        return new BlockGroup(block.content(), block.type(), section, block.location(),
                block.metadata(), true, context(section));
    }

    static String context(List<String> section) {
        return String.join(CONTEXT_SEPARATOR, section);
    }

    static String join(List<DocumentBlock> blocks) {
        var texts = blocks.stream().map(DocumentBlock::content).filter(text -> !text.isBlank()).toList();
        return String.join(BLOCK_SEPARATOR, texts);
    }

    static BlockType contentType(List<DocumentBlock> blocks) {
        if (blocks.stream().allMatch(block -> block.type() == BlockType.LIST)) return BlockType.LIST;
        if (blocks.stream().allMatch(block -> block.type() == BlockType.QUOTE)) return BlockType.QUOTE;
        return BlockType.PARAGRAPH;
    }

    static Map<String, Object> metadata(List<DocumentBlock> blocks) {
        return blocks.isEmpty() ? Map.of() : blocks.getFirst().metadata();
    }

    static final class GroupAccumulator {
        private final int maxChars;
        private final List<DocumentBlock> blocks = new ArrayList<>();
        private List<String> section = List.of();
        private Integer page;

        GroupAccumulator(int maxChars) {
            this.maxChars = maxChars;
        }

        void add(DocumentBlock block, List<String> nextSection) {
            if (blocks.isEmpty()) {
                section = nextSection;
                page = block.location().page();
            }
            blocks.add(block);
        }

        boolean overLimit() {
            return join(blocks).length() > maxChars;
        }

        boolean pageChanged(DocumentBlock block) {
            var next = block.location().page();
            return !blocks.isEmpty() && next != null && !next.equals(page);
        }

        void flush(List<BlockGroup> groups) {
            if (blocks.isEmpty()) return;
            groups.add(new BlockGroup(join(blocks), contentType(blocks), section,
                    blocks.getFirst().location(), metadata(blocks), false, context(section)));
            blocks.clear();
        }
    }
}
