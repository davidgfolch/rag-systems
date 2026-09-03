package com.rag.common.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.repositories.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncIngestionServiceTest {

    private final DocumentParser parser = mock(DocumentParser.class);
    private final TextSplitter splitter = mock(TextSplitter.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final VectorStore vectorStore = mock(VectorStore.class);

    private IngestionService ingestionService() {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore);
    }

    @Test
    void submitsAndTransitionsToCompletedWithChunkCount() {
        IngestionService delegate = ingestionService();
        when(parser.parse(any())).thenReturn("hello world");
        Chunk chunk = new Chunk("c1", "d1", "hello world", 0, Map.of());
        when(splitter.split(any())).thenReturn(List.of(chunk));
        when(embeddingModel.embed("hello world")).thenReturn(List.of(1.0f, 0.0f));

        AsyncIngestionService service = new AsyncIngestionService(delegate);
        String id = service.submit(new Document("d1", "", Map.of("rawBytes", new byte[]{1})));

        AsyncIngestionService.JobStatus status = await(service, id);
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.chunkCount()).isEqualTo(1);
    }

    @Test
    void marksFailedWhenIngestionThrows() {
        IngestionService delegate = mock(IngestionService.class);
        when(delegate.ingest(any())).thenThrow(new IllegalStateException("boom"));

        AsyncIngestionService service = new AsyncIngestionService(delegate);
        String id = service.submit(new Document("d1", "content", Map.of()));

        AsyncIngestionService.JobStatus status = await(service, id);
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("boom");
    }

    @Test
    void failsFastWithoutIngestingWhenVectorStoreUnavailable() {
        IngestionService delegate = mock(IngestionService.class);
        VectorStore store = mock(VectorStore.class);
        doThrow(new IllegalStateException("Vector store not available: DataAccessResourceFailureException: Connection refused"))
                .when(store).checkAvailable();

        AsyncIngestionService service = new AsyncIngestionService(delegate, store, null);
        String id = service.submit(new Document("d1", "content", Map.of()));

        AsyncIngestionService.JobStatus status = await(service, id);
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("Connection refused");
        verify(delegate, never()).ingest(any());
    }

    @Test
    void reportsFailedForUnknownJob() {
        AsyncIngestionService service = new AsyncIngestionService(mock(IngestionService.class));

        AsyncIngestionService.JobStatus status = service.status("nope");

        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("No such ingestion job");
    }

    private static AsyncIngestionService.JobStatus await(AsyncIngestionService service, String id) {
        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> isTerminal(service.status(id)));
        return service.status(id);
    }

    private static boolean isTerminal(AsyncIngestionService.JobStatus status) {
        return !"PENDING".equals(status.state()) && !"RUNNING".equals(status.state());
    }
}
