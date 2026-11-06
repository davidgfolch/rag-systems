package com.rag.common.core.domain;

import java.util.List;
import java.util.Map;

/**
 * One ordered structural unit of a parsed document (heading, paragraph, table,
 * code block, JSON record, ...). Immutable.
 */
public record DocumentBlock(BlockType type, String content, List<String> sectionPath,
                            SourceLocation location, Map<String, Object> metadata) {

    public DocumentBlock {
        type = type == null ? BlockType.PARAGRAPH : type;
        content = content == null ? "" : content;
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        location = location == null ? SourceLocation.NONE : location;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public DocumentBlock(BlockType type, String content) {
        this(type, content, List.of(), SourceLocation.NONE, Map.of());
    }

    /** The immediate heading that introduces this block, or {@code null} at the root. */
    public String heading() {
        return sectionPath.isEmpty() ? null : sectionPath.getLast();
    }

    public boolean isBlank() {
        return content == null || content.isBlank();
    }
}
