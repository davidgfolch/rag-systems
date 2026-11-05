package com.rag.common.app.pipeline;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.repositories.VectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveVectorStoreTest {

    private final VariantStore store = mock(VariantStore.class);
    private final VectorStorePort delegate = mock(VectorStorePort.class);
    private final ActiveVectorStore sut = new ActiveVectorStore(store);

    @BeforeEach
    void setUp() {
        when(store.store()).thenReturn(delegate);
    }

    @Test
    void delegatesEveryOperationToTheActiveVariantsStore() {
        var chunks = List.of(new Chunk("c1", "doc-1", "text", 0, Map.of()));
        when(delegate.similaritySearch("query", 3)).thenReturn(chunks);
        when(delegate.similaritySearch("query", 3, "doc-1")).thenReturn(chunks);

        sut.add(chunks);
        assertThat(sut.similaritySearch("query", 3)).isSameAs(chunks);
        assertThat(sut.similaritySearch("query", 3, "doc-1")).isSameAs(chunks);
        assertThat(sut.listDocuments()).isEmpty();
        sut.delete("doc-1");
        sut.checkAvailable();

        verify(delegate).add(chunks);
        verify(delegate).delete("doc-1");
        verify(delegate).checkAvailable();
    }
}
