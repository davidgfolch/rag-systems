package com.rag.common.ingestion.chunking.structural;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.SourceLocation;

import java.util.List;
import java.util.Map;

/**
 * One candidate chunk before the generic splitting algorithm runs. The context
 * is the rendered section path that must accompany the group in the chunk text.
 */
public record BlockGroup(String text, BlockType contentType, List<String> sectionPath,
                         SourceLocation location, Map<String, Object> metadata,
                         boolean protectedBlock, String context) {

    public BlockGroup {
        text = text == null ? "" : text;
        contentType = contentType == null ? BlockType.PARAGRAPH : contentType;
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        location = location == null ? SourceLocation.NONE : location;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        context = context == null ? "" : context;
    }
}
