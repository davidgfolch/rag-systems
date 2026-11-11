package com.rag.basic.api;

import com.rag.basic.services.RetrievalService;
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

    private final RetrievalService service = mock(RetrievalService.class);
    private final QueryController controller = new QueryController(service);

    private Chunk chunk() {
        return new Chunk("c1", "d1", "content", 1, Map.of());
    }

    @Test
    void returnsRetrievedChunks() {
        when(service.retrieve("q", 3)).thenReturn(List.of(chunk()));
        QueryResponse res = controller.query(new QueryRequest().question("q").topK(3));
        assertThat(res.getQuestion()).isEqualTo("q");
        assertThat(res.getResults()).hasSize(1);
        ChunkResult result = res.getResults().getFirst();
        assertThat(result.getId()).isEqualTo("c1");
        assertThat(result.getDocumentId()).isEqualTo("d1");
        assertThat(result.getContent()).isEqualTo("content");
        assertThat(result.getIndex()).isEqualTo(1);
    }

    @Test
    void passesDocumentIdScoping() {
        controller.query(new QueryRequest().question("q").documentId("d1"));
        verify(service).retrieve("q", 5, "d1");
    }

    @Test
    void defaultsTopKToFiveWhenNull() {
        controller.query(new QueryRequest().question("q").topK(null));
        verify(service).retrieve("q", 5);
    }

    @Test
    void returnsEmptyResultsWhenNoMatches() {
        when(service.retrieve("q", 5)).thenReturn(List.of());
        QueryResponse res = controller.query(new QueryRequest().question("q"));
        assertThat(res.getResults()).isEmpty();
    }
}