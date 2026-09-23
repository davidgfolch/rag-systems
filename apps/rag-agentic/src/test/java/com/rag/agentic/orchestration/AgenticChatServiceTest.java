package com.rag.agentic.orchestration;

import com.rag.agentic.domain.AgentStep;
import com.rag.agentic.domain.AgentTrace;
import com.rag.common.generation.StreamingChatModelPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static com.rag.common.core.testfixture.TestChunks.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenticChatServiceTest {

    private final AgentLoop loop = mock(AgentLoop.class);
    private final StreamingChatModelPort streamingModel = mock(StreamingChatModelPort.class);
    private final AgenticChatService sut = new AgenticChatService(loop, streamingModel);

    @Test
    void askRunsLoopAndExposesAnswerSourcesAndSteps() {
        var steps = List.of(new AgentStep("tool:search", "snippet"));
        when(loop.run("q", 5)).thenReturn(new AgentTrace("answer", list("a", "b"), steps));
        var result = sut.ask("q", 5);
        assertThat(result.answer()).isEqualTo("answer");
        assertThat(result.sources()).hasSize(2);
        assertThat(result.steps()).isEqualTo(steps);
        assertThat(result.sources().get(0).getId()).isEqualTo("chunk-0");
    }

    @Test
    void askStreamGathersContextAndStreamsAnswerTokens() {
        when(loop.collect("q", 5)).thenReturn(new AgentTrace(null, list("a"), List.of(), "context text"));
        when(streamingModel.completeStream(anyString())).thenReturn(Flux.just("tok1", "tok2"));
        var flux = sut.askStream("q", 5);
        assertThat(flux.collectList().block()).containsExactly("tok1", "tok2");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(streamingModel).completeStream(prompt.capture());
        assertThat(prompt.getValue()).contains("context text", "Question: q");
    }

    @Test
    void askStreamNeverCallsNonStreamingModel() {
        when(loop.collect(anyString(), anyInt())).thenReturn(new AgentTrace(null, List.of(), List.of(), ""));
        when(streamingModel.completeStream(anyString())).thenReturn(Flux.empty());
        assertThat(sut.askStream("q", 5).collectList().block()).isEmpty();
        verify(loop).collect("q", 5);
    }
}