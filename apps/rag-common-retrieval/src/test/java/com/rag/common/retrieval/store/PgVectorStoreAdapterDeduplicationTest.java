package com.rag.common.retrieval.store;

import com.rag.common.core.domain.Chunk;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorStoreAdapterDeduplicationTest {

    private final VectorStore delegate = mock(VectorStore.class);
    private final PgVectorStoreAdapter adapter = new PgVectorStoreAdapter(delegate);

    @Test
    void collapsesDuplicateContentAndKeepsTheHighestRankedOccurrence() {
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                springDoc("c1", "Kafka Streams > use cases"),
                springDoc("c2", "Kafka Streams > use cases"),
                springDoc("c3", "Kafka Streams is a client library")));

        var result = adapter.similaritySearch("query", 2);

        assertThat(result).extracting(Chunk::getId).containsExactly("c1", "c3");
    }

    @Test
    void overfetchesCandidatesSoDuplicatesCannotStarveTopK() {
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adapter.similaritySearch("query", 2);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.captor();
        verify(delegate).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(ContentDeduplicator.fetchSize(2));
    }

    @Test
    void documentScopedSearchKeepsTheFilterAndDeduplicates() {
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                springDoc("c1", "same text"),
                springDoc("c2", "same text"),
                springDoc("c3", "other text")));

        var result = adapter.similaritySearch("query", 2, "d1");

        assertThat(result).extracting(Chunk::getId).containsExactly("c1", "c3");
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.captor();
        verify(delegate).similaritySearch(captor.capture());
        assertThat(captor.getValue().hasFilterExpression()).isTrue();
        assertThat(captor.getValue().getFilterExpression().toString()).contains("d1");
    }

    private static Document springDoc(String id, String text) {
        return new Document.Builder().id(id).text(text).build();
    }
}
