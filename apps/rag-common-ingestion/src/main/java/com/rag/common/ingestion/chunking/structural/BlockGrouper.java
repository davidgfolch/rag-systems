package com.rag.common.ingestion.chunking.structural;

import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.services.ChunkingConfig;

import java.util.List;

/** Turns ordered document blocks into structure-aware candidate groups. */
public interface BlockGrouper {

    List<BlockGroup> group(ParsedDocument document, ChunkingConfig config, int maxChars);
}
