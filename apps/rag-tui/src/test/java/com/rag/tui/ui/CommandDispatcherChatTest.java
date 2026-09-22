package com.rag.tui.ui;

import com.rag.contract.model.ChatMessageDTO;
import com.rag.contract.model.ConversationDTO;
import com.rag.tui.client.ChatGateway;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.rag.tui.testfixture.TestModules.basic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherChatTest extends AbstractCommandDispatcherTest {

    @Test
    void streamsAskTokens() {
        List<String> tokens = new ArrayList<>();
        when(chatGateway.ask(eq("what is rag"), eq(4), any())).thenAnswer(invocation -> {
            Consumer<String> sink = invocation.getArgument(2);
            sink.accept("tok1");
            sink.accept("tok2");
            return "tok1tok2";
        });
        sut.handle("ask what is rag", tokens::add);
        assertThat(tokens).containsExactly("tok1", "tok2");
    }

    @Test
    void showsHistoryFromMemory() {
        ConversationDTO conversation = new ConversationDTO().id("c1").title("t1");
        when(memoryClient.conversations()).thenReturn(List.of(conversation));
        when(memoryClient.messages("c1")).thenReturn(List.of(
                new ChatMessageDTO().content("hi")));
        var result = handle("history");
        assertThat(result).contains("c1", "t1", "1 messages");
    }

    @Test
    void showsEmptyHistory() {
        when(memoryClient.conversations()).thenReturn(List.of());
        var result = handle("history");
        assertThat(result).contains("No conversations yet");
    }

    @Test
    void quits() {
        assertThatThrownBy(() -> handle("quit"))
                .isInstanceOf(ShellExitException.class);
    }

    @Test
    void wrapsEachCommandInATuiCommandSpan() {
        when(chatGateway.ask(eq("hi"), eq(4), any())).thenReturn("");
        var tracer = new SimpleTracer();
        var dispatcher = new CommandDispatcher(registry, lifecycle,
                new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient, fileLoader, healthClient,
                        providerClient),
                new CommandDispatcher.Settings(10_000, 4, 60), commandRegistry, prompter, tracer);
        dispatcher.handle("ask hi", t -> {});
        assertThat(tracer.getSpans()).hasSize(1);
        assertThat(tracer.getSpans().getLast().getName()).isEqualTo(CommandDispatcher.SPAN_COMMAND);
        assertThat(tracer.getSpans().getLast().getEndTimestamp()).isNotNull();
    }

    @Test
    void rejectsUnknownCommand() {
        var result = handle("frobnicate");
        assertThat(result).contains("Unknown command");
    }

    @Test
    void reportsChatErrorsWithoutCrashing() {
        when(chatGateway.ask(eq("hello"), eq(4), any()))
                .thenThrow(new ChatGateway.ChatException("Module " + basic().wsUrl() + " unreachable", null));
        var result = handle("ask hello");
        assertThat(result).contains("Chat error", "unreachable");
    }

    @Test
    void emptyInputReturnsEmptyString() {
        assertThat(handle("")).isEmpty();
        assertThat(handle("   ")).isEmpty();
    }

    @Test
    void askCancelledPromptShowsUsage() {
        when(prompter.prompt("Question: ")).thenReturn(null);
        var result = handle("ask");
        assertThat(result).isEqualTo("Usage: ask <question>");
        verify(chatGateway, never()).ask(anyString(), anyInt(), any());
    }

    @Test
    void showsHistoryWithNullConversationTitle() {
        ConversationDTO conversation = new ConversationDTO().id("c2");
        when(memoryClient.conversations()).thenReturn(List.of(conversation));
        when(memoryClient.messages("c2")).thenReturn(List.of(new ChatMessageDTO().content("hi")));
        var result = handle("history");
        assertThat(result).contains("c2", "1 messages");
    }
}