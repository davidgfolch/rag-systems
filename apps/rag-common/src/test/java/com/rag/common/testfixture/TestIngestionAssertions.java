package com.rag.common.testfixture;

import com.rag.common.services.IngestionService;
import com.rag.contract.model.IngestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public final class TestIngestionAssertions {

    private TestIngestionAssertions() {
    }

    public static void assertDocumentCreated(ResponseEntity<IngestResponse> response, String id, int chunkCount) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getDocumentId()).isEqualTo(id);
        assertThat(response.getBody().getChunkCount()).isEqualTo(chunkCount);
    }

    public static void assertBadRequestAndNotIngested(ResponseEntity<?> response, IngestionService service) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).ingest(any());
    }
}
