package com.rag.common.app.pipeline;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.services.TextSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveTextSplitterTest {

    @Test
    void splitsWithTheActiveVariantsSplitter() {
        var store = mock(VariantStore.class);
        var splitter = mock(TextSplitter.class);
        var document = new Document("doc-1", "hello world", Map.of());
        var chunks = List.of(new Chunk("c1", "doc-1", "hello world", 0, Map.of()));
        when(store.splitter()).thenReturn(splitter);
        when(splitter.split(document)).thenReturn(chunks);

        assertThat(new ActiveTextSplitter(store).split(document)).isSameAs(chunks);
    }
}
