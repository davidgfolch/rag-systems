package com.rag.advanced.api;

import com.rag.advanced.services.AdvancedRetrievalService;
import com.rag.common.core.domain.Chunk;
import com.rag.contract.model.ChunkResult;
import com.rag.contract.model.QueryRequest;
import com.rag.contract.model.QueryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryControllerTest {

    private final AdvancedRetrievalService service = mock(AdvancedRetrievalService.class);
    private final QueryController controller = new QueryController(service);

    private Chunk chunk() {
        return new Chunk("c1", "d1", "content", 1, Map.of());
    }

    @Test
    void returnsRetrievedChunks() {
        when(service.retrieve("q", 3, Map.of())).thenReturn(List.of(chunk()));
        QueryResponse response = controller.query(new QueryRequest().question("q").topK(3));
        assertThat(response.getQuestion()).isEqualTo("q");
        assertThat(response.getResults()).hasSize(1);
        ChunkResult result = response.getResults().get(0);
        assertThat(result.getId()).isEqualTo("c1");
        assertThat(result.getDocumentId()).isEqualTo("d1");
        assertThat(result.getContent()).isEqualTo("content");
        assertThat(result.getIndex()).isEqualTo(1);
    }

    @Test
    void passesDocumentIdScoping() {
        controller.query(new QueryRequest().question("q").documentId("d1"));
        verify(service).retrieve("q", 5, "d1", Map.of());
    }

    @Test
    void defaultsTopKToFiveWhenNull() {
        controller.query(new QueryRequest().question("q").topK(null));
        verify(service).retrieve("q", 5, Map.of());
    }

    @Test
    void returnsEmptyResultsWhenNoMatches() {
        when(service.retrieve("q", 5, Map.of())).thenReturn(List.of());
        QueryResponse response = controller.query(new QueryRequest().question("q"));
        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void advancedQueryAppliesMetadataFilter() {
        when(service.retrieve("q", 5, Map.of("source", "web"))).thenReturn(List.of(chunk()));
        QueryResponse response = controller.advancedQuery(
                new AdvancedQueryRequest("q", 5, null, Map.of("source", "web")));
        assertThat(response.getQuestion()).isEqualTo("q");
        assertThat(response.getResults()).hasSize(1);
    }

    @Test
    void advancedQueryScopesByDocumentWhenProvided() {
        controller.advancedQuery(new AdvancedQueryRequest("q", 5, "d1", null));
        verify(service).retrieve("q", 5, "d1", Map.of());
    }

    @Test
    void advancedQueryDefaultsTopKAndEmptyMetadata() {
        controller.advancedQuery(new AdvancedQueryRequest("q", null, null, null));
        verify(service).retrieve("q", 5, Map.of());
    }
}