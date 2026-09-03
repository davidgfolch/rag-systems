package com.rag.basic.api;

import com.rag.basic.services.RetrievalService;
import com.rag.basic.services.WebCrawlerClient;
import com.rag.common.domain.Chunk;
import com.rag.common.repositories.store.InMemoryVectorStore;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModel;
import com.rag.common.services.IngestionService;
import com.rag.common.services.TextSplitter;
import com.rag.common.services.chunking.RecursiveCharacterChunker;
import com.rag.common.services.parsing.TikaDocumentParser;
import com.rag.contract.model.IngestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test of the file-ingestion pipeline exactly as the
 * TUI {@code add-file} command drives it: multipart PDF upload -> Tika parse ->
 * recursive chunk -> embed -> in-memory store, then retrieval of the ingested
 * content. Uses the real strategy implementations; only the embedding provider
 * is stubbed so no external service is required.
 */
class PdfIngestionIntegrationTest {

    private final EmbeddingModel stubEmbedding = new HashEmbeddingModel();

    private final InMemoryVectorStore store = new InMemoryVectorStore(stubEmbedding);
    private final DocumentParser parser = new TikaDocumentParser();
    private final TextSplitter splitter = new RecursiveCharacterChunker(120, 24);
    private final IngestionService ingestionService =
            new IngestionService(parser, splitter, stubEmbedding, store);
    private final RetrievalService retrievalService = new RetrievalService(store);
    private final IngestionController controller =
            new IngestionController(ingestionService, new WebCrawlerClient(null));

    @Test
    void ingestsTextPdfIntoChunksAndRetrievesThem() throws Exception {
        byte[] pdfBytes = textPdf("""
                Learning Domain-Driven Design

                This book explains how to apply domain-driven design to software
                systems. It covers tactical patterns such as aggregates, value
                objects, and domain services, alongside strategic design.""");

        ResponseEntity<IngestResponse> response = controller.ingestFile(
                new MockMultipartFile("file", "book.pdf", "application/pdf", pdfBytes),
                Map.of("sourceType", "file", "fileName", "book.pdf"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getDocumentId()).isNotBlank();
        assertThat(response.getBody().getChunkCount()).isPositive();

        List<Chunk> hits = retrievalService.retrieve("domain-driven design aggregates", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.stream().map(Chunk::getContent).anyMatch(c -> c.contains("domain-driven design")))
                .isTrue();
    }

    @Test
    void rejectsEmptyFileWithBadRequest() throws Exception {
        ResponseEntity<IngestResponse> response = controller.ingestFile(
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]),
                Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsBinaryFileWithNoExtractableText() {
        MockMultipartFile scanned = new MockMultipartFile(
                "file", "scanned.pdf", "application/pdf", imageOnlyPdf());

        assertThatThrownBy(() -> controller.ingestFile(scanned,
                Map.of("sourceType", "file", "fileName", "scanned.pdf")))
                .isInstanceOf(IngestionService.EmptyExtractionException.class)
                .hasMessageContaining("No text could be extracted");
    }

    /**
     * A minimal, structurally valid PDF with NO text-drawing operators: mimics a
     * scanned/image-based document from which Tika can extract no text.
     */
    private static byte[] imageOnlyPdf() {
        String stream = "q\n";
        String pdf = "%PDF-1.4\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                + "/Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n"
                + "4 0 obj\n<< /Length " + stream.length() + " >>\nstream\n" + stream + "endstream\nendobj\n"
                + "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
                + "xref\n0 6\n"
                + "0000000000 65535 f \n"
                + "0000000009 00000 n \n"
                + "0000000058 00000 n \n"
                + "0000000115 00000 n \n"
                + "0000000268 00000 n \n"
                + "0000000415 00000 n \n"
                + "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n480\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] textPdf(String body) {
        String escaped = body.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        String stream = "BT /F1 12 Tf 72 720 Td (" + escaped + ") Tj ET\n";
        String pdf = "%PDF-1.4\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                + "/Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n"
                + "4 0 obj\n<< /Length " + stream.length() + " >>\nstream\n" + stream + "endstream\nendobj\n"
                + "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
                + "xref\n0 6\n"
                + "0000000000 65535 f \n"
                + "0000000009 00000 n \n"
                + "0000000058 00000 n \n"
                + "0000000115 00000 n \n"
                + "0000000268 00000 n \n"
                + "0000000415 00000 n \n"
                + "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n480\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Deterministic, collision-safe-enough embedding for integration testing. */
    private static final class HashEmbeddingModel implements EmbeddingModel {
        @Override
        public List<Float> embed(String text) {
            return text.chars()
                    .mapToObj(c -> (float) (c % 7))
                    .toList();
        }
    }
}
