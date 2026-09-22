package com.rag.basic;

import com.rag.common.core.domain.MetadataKeys;
import com.rag.common.retrieval.testcontainers.PostgresContainerConfig;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestStatusDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

/**
 * Reproduces the reported bug: {@code add-file} / {@code add-folder} dedup does
 * not work and identical documents end up duplicated in PgVector. Boots the
 * real rag-basic context against a Testcontainers pgvector store and drives the
 * multipart request the TUI sends (binary file + JSON {@code fileInfo} carrying
 * a content-hash), then checks that the canonical SHA-256 hash computed from
 * the uploaded bytes is stored in the document metadata - the single source of
 * truth the TUI's {@code DuplicateFinder} dedups against via
 * {@code GET /api/documents}. The duplicate case documents that the server has
 * no dedup of its own, so the TUI's in-flight guard is what must prevent it.
 */
@ContextConfiguration(initializers = PostgresContainerConfig.class)
@SpringBootTest(classes = RagBasicApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "rag.vector-store.type=pgvector",
                "spring.ai.vectorstore.pgvector.schema-name=rag_dedup_repro",
                "spring.ai.vectorstore.pgvector.table-name=chunks",
                "spring.ai.vectorstore.pgvector.initialize-schema=true"
        })
@Import(AsyncFileDedupReproductionTest.DeterministicEmbeddingConfig.class)
class AsyncFileDedupReproductionTest {

    private static final int DIM = 64;
    private static final String MARKER_KEY = "marker";

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void contentHashSurvivesAsyncIngestIntoPgVectorMetadata() {
        String text = "first note content";
        var documentId = submit(getTuiStyleBody(text, "report.txt", "client-sent-hash-one", "marker-one"));

        await().atMost(ofSeconds(15)).until(() -> state(documentId) == IngestStatusDTO.StateEnum.COMPLETED);

        var summary = summaryFor("marker-one");
        assertThat(summary).isPresent();
        assertThat(summary.get().getMetadata())
                .containsEntry(MetadataKeys.CONTENT_HASH, sha256(text))
                .doesNotContainEntry(MetadataKeys.CONTENT_HASH, "client-sent-hash-one");
        assertThat(marker(summary.get())).isEqualTo("marker-one");
    }

    @Test
    void identicalBytesUploadedTwiceReproduceDuplicatedDocuments() {
        String text = "repeated identical content";
        submit(getTuiStyleBody(text, "dup.txt", "client-sent-hash-two", "marker-two"));
        submit(getTuiStyleBody(text, "dup.txt", "client-sent-hash-two", "marker-two"));

        await().atMost(ofSeconds(15)).until(() -> summariesFor("marker-two").size() >= 2);

        var duplicates = summariesFor("marker-two");
        assertThat(duplicates).hasSize(2)
                .allSatisfy(d -> assertThat(d.getMetadata())
                        .containsEntry(MetadataKeys.CONTENT_HASH, sha256(text)));
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String submit(org.springframework.util.MultiValueMap<String, Object> body) {
        var response = client().post()
                .uri("/api/documents/ingest-file-async")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(IngestJobResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.getDocumentId()).isNotBlank();
        return response.getDocumentId();
    }

    private IngestStatusDTO.StateEnum state(String documentId) {
        var status = client().get()
                .uri("/api/documents/ingest-status/{id}", documentId)
                .retrieve()
                .body(IngestStatusDTO.class);
        return status == null ? null : status.getState();
    }

    private List<DocumentSummaryDTO> summaries() {
        var result = client().get()
                .uri("/api/documents")
                .retrieve()
                .body(DocumentSummaryDTO[].class);
        return result == null ? List.of() : List.of(result);
    }

    private Optional<DocumentSummaryDTO> summaryFor(String markerValue) {
        return summaries().stream()
                .filter(d -> markerValue.equals(marker(d)))
                .findFirst();
    }

    private List<DocumentSummaryDTO> summariesFor(String markerValue) {
        return summaries().stream()
                .filter(d -> markerValue.equals(marker(d)))
                .toList();
    }

    private static Object marker(DocumentSummaryDTO doc) {
        Map<String, Object> metadata = doc.getMetadata();
        return metadata == null ? null : metadata.get(MARKER_KEY);
    }

    /**
     * Builds the multipart body exactly as {@code RagApiClient.buildBody} does in
     * rag-tui: a binary {@code file} part plus an application/json {@code fileInfo}
     * part holding the document metadata (including {@code contentHash}).
     */
    private static org.springframework.util.MultiValueMap<String, Object> getTuiStyleBody(
            String text, String fileName, String contentHash, String marker) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        var jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("fileInfo", new HttpEntity<>(Map.of(
                MetadataKeys.SOURCE_TYPE, "file",
                MetadataKeys.FILE_NAME, fileName,
                MetadataKeys.CONTENT_HASH, contentHash,
                MARKER_KEY, marker), jsonHeaders));
        return body;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicEmbeddingConfig {

        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new CharHashEmbeddingModel();
        }
    }

    private static final class CharHashEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            var results = request.getInstructions().stream()
                    .map(text -> new Embedding(embedText(text), 0))
                    .toList();
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(Document document) {
            return embedText(document.getText());
        }

        private static float[] embedText(String text) {
            var vec = new float[DIM];
            text.toLowerCase().chars().forEach(c -> vec[Math.floorMod(c, DIM)] += 1f);
            return vec;
        }
    }
}