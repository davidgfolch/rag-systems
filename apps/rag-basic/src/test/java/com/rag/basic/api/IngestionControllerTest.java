package com.rag.basic.api;

import com.rag.basic.api.dto.IngestRequest;
import com.rag.basic.api.dto.IngestResponse;
import com.rag.basic.services.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionControllerTest {

    private final IngestionService service = mock(IngestionService.class);
    private final IngestionController controller = new IngestionController(service);

    @Test
    void ingestsValidRequest() {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 5));

        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest("some content", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().documentId()).isEqualTo("d1");
        assertThat(response.getBody().chunkCount()).isEqualTo(5);
    }

    @Test
    void rejectsBlankContent() {
        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest("   ", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).ingest(any());
    }
}