package com.rag.basic.api;

import com.rag.basic.api.dto.QueryResponse;
import com.rag.basic.services.RetrievalService;
import com.rag.common.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryControllerTest {

    private final RetrievalService service = mock(RetrievalService.class);
    private final QueryController controller = new QueryController(service);

    @Test
    void returnsRetrievedChunks() {
        Chunk chunk = new Chunk("c1", "d1", "content", 1, Map.of());
        when(service.retrieve(eq("q"), eq(3))).thenReturn(List.of(chunk));

        QueryResponse response = controller.query("q", 3);

        assertThat(response.query()).isEqualTo("q");
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).id()).isEqualTo("c1");
        assertThat(response.results().get(0).documentId()).isEqualTo("d1");
        assertThat(response.results().get(0).content()).isEqualTo("content");
        assertThat(response.results().get(0).index()).isEqualTo(1);
    }

    @Test
    void returnsEmptyResultsWhenNoMatches() {
        when(service.retrieve(eq("q"), eq(5))).thenReturn(List.of());

        QueryResponse response = controller.query("q", 5);

        assertThat(response.results()).isEmpty();
    }
}