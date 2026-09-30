package com.rag.agentic.api;

import com.rag.agentic.domain.AgentStep;
import com.rag.agentic.orchestration.AgenticChatService;
import com.rag.agentic.services.AgenticRetrievalService;
import com.rag.contract.model.QueryRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.common.core.testfixture.TestChunks.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenticQueryControllerTest {

    private final AgenticRetrievalService retrievalService = mock(AgenticRetrievalService.class);
    private final AgenticChatService chatService = mock(AgenticChatService.class);
    private final AgenticQueryController controller = new AgenticQueryController(retrievalService, chatService);

    @Test
    void queryReturnsRetrievedChunks() {
        when(retrievalService.retrieve("q", 5)).thenReturn(list("a", "b"));
        var response = controller.query(new QueryRequest().question("q"));
        assertThat(response.getQuestion()).isEqualTo("q");
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(0).getId()).isEqualTo("chunk-0");
    }

    @Test
    void queryDefaultsTopKWhenNull() {
        controller.query(new QueryRequest().question("q").topK(null));
        verify(retrievalService).retrieve("q", 5);
    }

    @Test
    void queryUsesProvidedTopK() {
        controller.query(new QueryRequest().question("q").topK(7));
        verify(retrievalService).retrieve("q", 7);
    }

    @Test
    void agenticQueryExposesAnswerSourcesAndSteps() {
        var steps = List.of(new AgentStep("tool:search", "snippet"));
        when(chatService.ask("q", 5)).thenReturn(
                new AgenticChatService.AgentQueryResult("answer", list("a"), steps));
        var response = controller.agenticQuery(new AgentQueryRequest("q", null));
        assertThat(response.answer()).isEqualTo("answer");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).getContent()).isEqualTo("a");
        assertThat(response.steps()).isEqualTo(steps);
    }

    @Test
    void agenticQueryUsesProvidedTopK() {
        when(chatService.ask("q", 3)).thenReturn(
                new AgenticChatService.AgentQueryResult("answer", list("a"), List.of()));
        var response = controller.agenticQuery(new AgentQueryRequest("q", 3));
        verify(chatService).ask("q", 3);
        assertThat(response.answer()).isEqualTo("answer");
    }
}