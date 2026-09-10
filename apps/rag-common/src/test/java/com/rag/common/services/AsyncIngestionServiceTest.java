package com.rag.common.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.domain.MetadataKeys;
import com.rag.common.repositories.VectorStorePort;
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
    private final EmbeddingModelPort embeddingModel = mock(EmbeddingModelPort.class);
    private final VectorStorePort vectorStore = mock(VectorStorePort.class);

    private IngestionService ingestionService() {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore);
    }

    @Test
    void submitsAndTransitionsToCompletedWithChunkCount() {
        var delegate = ingestionService();
        when(parser.parse(any())).thenReturn("hello world");
        var chunk = new Chunk("c1", "d1", "hello world", 0, Map.of());
        when(splitter.split(any())).thenReturn(List.of(chunk));
        when(embeddingModel.embed("hello world")).thenReturn(List.of(1.0f, 0.0f));

        var service = new AsyncIngestionService(delegate);
        var id = service.submit(new Document("d1", "", Map.of(MetadataKeys.RAW_BYTES, new byte[]{1})));

        var status = await(service, id);
        assertThat(status.state()).isEqualTo("COMPLETED");
        assertThat(status.chunkCount()).isEqualTo(1);
    }

    @Test
    void marksFailedWhenIngestionThrows() {
        var delegate = mock(IngestionService.class);
        when(delegate.ingest(any())).thenThrow(new IllegalStateException("boom"));

        var service = new AsyncIngestionService(delegate);
        var id = service.submit(new Document("d1", "content", Map.of()));

        var status = await(service, id);
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("boom");
    }

    @Test
    void failsFastWithoutIngestingWhenVectorStoreUnavailable() {
        var delegate = mock(IngestionService.class);
        var store = mock(VectorStorePort.class);
        doThrow(new IllegalStateException("Vector store not available: DataAccessResourceFailureException: Connection refused"))
                .when(store).checkAvailable();

        var service = new AsyncIngestionService(delegate, store, null);
        var id = service.submit(new Document("d1", "content", Map.of()));

        var status = await(service, id);
        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.message()).contains("Connection refused");
        verify(delegate, never()).ingest(any());
    }

    @Test
    void reportsFailedForUnknownJob() {
        var service = new AsyncIngestionService(mock(IngestionService.class));

        var status = service.status("nope");

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
