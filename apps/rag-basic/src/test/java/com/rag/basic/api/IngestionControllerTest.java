package com.rag.basic.api;

import com.rag.basic.services.WebCrawlerClient;
import com.rag.common.services.IngestionService;
import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionControllerTest {

    private final IngestionService service = mock(IngestionService.class);
    private final WebCrawlerClient webCrawlerClient = mock(WebCrawlerClient.class);
    private final IngestionController controller = new IngestionController(service, webCrawlerClient);

    @Test
    void ingestsValidRequest() {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 5));

        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest().content("some content"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getDocumentId()).isEqualTo("d1");
        assertThat(response.getBody().getChunkCount()).isEqualTo(5);
    }

    @Test
    void rejectsBlankContent() {
        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest().content("   "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).ingest(any());
    }

    @Test
    void ingestsUrlViaWebCrawler() {
        PageDTO page = new PageDTO().url("https://example.com").title("Example").text("page text");
        when(webCrawlerClient.fetch("https://example.com")).thenReturn(page);
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d2", 3));

        ResponseEntity<IngestResponse> response =
                controller.ingestUrl(new IngestUrlRequest().url(URI.create("https://example.com")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getDocumentId()).isEqualTo("d2");
        assertThat(response.getBody().getChunkCount()).isEqualTo(3);
    }

    @Test
    void rejectsBlankUrl() {
        ResponseEntity<IngestResponse> response =
                controller.ingestUrl(new IngestUrlRequest().url(URI.create("")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).ingest(any());
    }
}