package com.rag.common.ingestion.testfixture;

import com.rag.common.ingestion.IngestionService;
import com.rag.contract.model.IngestResponse;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;

public final class TestIngestionAssertions {

    private TestIngestionAssertions() {
    }

    public static void assertDocumentCreated(ResponseEntity<IngestResponse> res, String id, int chunkCount) {
        assertThat(res.getStatusCode()).isEqualTo(CREATED);
        assertThat(res.getBody().getDocumentId()).isEqualTo(id);
        assertThat(res.getBody().getChunkCount()).isEqualTo(chunkCount);
    }

    public static void assertBadRequestAndNotIngested(ResponseEntity<?> res, IngestionService service) {
        assertThat(res.getStatusCode()).isEqualTo(BAD_REQUEST);
        verify(service, never()).ingest(any());
    }
}
