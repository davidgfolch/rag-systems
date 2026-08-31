package com.rag.tui.ui;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.tui.fetching.WebPage;
import com.rag.tui.fetching.WebPageFetcher;
import com.rag.tui.services.ChatService;
import com.rag.tui.services.FileDocumentLoader;
import com.rag.tui.services.IngestionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherTest {

    private final IngestionService ingestionService = mock(IngestionService.class);
    private final ChatService chatService = mock(ChatService.class);
    private final WebPageFetcher webPageFetcher = mock(WebPageFetcher.class);
    private final FileDocumentLoader fileLoader = mock(FileDocumentLoader.class);

    private final CommandDispatcher sut =
            new CommandDispatcher(ingestionService, chatService, webPageFetcher, fileLoader);

    @Test
    void dispatchesAddFileCommand() {
        Document doc = new Document("doc-file", "content", Map.of());
        when(fileLoader.load("C:/docs/report.pdf")).thenReturn(doc);
        when(ingestionService.ingest(doc)).thenReturn(new IngestionService.IngestionResult("doc-file", 7));

        CommandResult result = sut.handle("add-file C:/docs/report.pdf");

        assertThat(result.exit()).isFalse();
        assertThat(result.message()).contains("7");
        verify(ingestionService).ingest(doc);
    }

    @Test
    void dispatchesAddUrlCommand() {
        when(webPageFetcher.fetch("https://example.com")).thenReturn(new WebPage("https://example.com", "web text"));
        when(ingestionService.ingest(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new IngestionService.IngestionResult("web", 3));

        CommandResult result = sut.handle("add-url https://example.com");

        assertThat(result.exit()).isFalse();
        assertThat(result.message()).contains("3");
        verify(ingestionService).ingest(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dispatchesAskCommand() {
        Chunk source = new Chunk("c1", "d1", "retrieved text", 0, Map.of());
        when(chatService.ask("what is rag", 4)).thenReturn(new ChatService.ChatResult("Rag is retrieval.", List.of(source)));

        CommandResult result = sut.handle("ask what is rag");

        assertThat(result.exit()).isFalse();
        assertThat(result.message()).contains("Rag is retrieval.");
        assertThat(result.message()).contains("retrieved text");
    }

    @Test
    void flagsExitOnQuit() {
        CommandResult result = sut.handle("quit");
        assertThat(result.exit()).isTrue();
    }

    @Test
    void printsHelpForUnknownCommand() {
        CommandResult result = sut.handle("bogus stuff");
        assertThat(result.exit()).isFalse();
        assertThat(result.message()).contains("help");
    }

    @Test
    void rejectsAddFileWithoutPath() {
        CommandResult result = sut.handle("add-file");
        assertThat(result.exit()).isFalse();
        assertThat(result.message()).containsIgnoringCase("usage");
    }
}
