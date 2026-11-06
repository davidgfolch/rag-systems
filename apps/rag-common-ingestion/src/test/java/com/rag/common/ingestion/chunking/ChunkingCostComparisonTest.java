package com.rag.common.ingestion.chunking;

import com.rag.common.core.domain.Document;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares the ingestion embedding cost of the chunkers on this repository's own
 * documentation, with a deterministic hashing embedding model instead of a
 * provider: the numbers are embedding <em>calls</em> and local split time, not
 * provider latency.
 *
 * <p>A semantic split embeds every sentence on top of the per-chunk embeddings
 * {@code IngestionService} issues afterwards, so its ingestion call count is
 * {@code sentences + chunks} where the other strategies pay {@code chunks}.
 */
class ChunkingCostComparisonTest {

    private static final Path CORPUS = Path.of("..", "..", "docs");
    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;
    private static final int VECTOR_DIMENSIONS = 64;
    private static final int MEASURED_PASSES = 3;
    private static final Pattern TOKEN_BOUNDARY = Pattern.compile("\\W+");

    @Test
    void semanticChunkingPaysExtraEmbeddingCallsForItsBoundaries() {
        var documents = corpus();
        var fixedCost = measure("fixed", documents, _ -> new FixedSizeChunker(CHUNK_SIZE, CHUNK_OVERLAP));
        var recursiveCost = measure("recursive", documents,
                _ -> new RecursiveCharacterChunker(CHUNK_SIZE, CHUNK_OVERLAP));
        var tokenCost = measure("token", documents,
                _ -> new TokenChunker(CHUNK_SIZE / 4, CHUNK_OVERLAP / 4));
        var semanticCost = measure("semantic", documents,
                embeddings -> new SemanticChunker(embeddings, CHUNK_SIZE, CHUNK_OVERLAP));

        System.out.printf(Locale.ROOT, "%nChunking cost over %d docs (size=%d, overlap=%d)%n",
                documents.size(), CHUNK_SIZE, CHUNK_OVERLAP);
        System.out.printf(Locale.ROOT, "%-10s %8s %11s %13s %15s %7s %9s%n",
                "strategy", "chunks", "chunks/doc", "sentence emb", "ingestion emb", "ratio", "split ms");
        Stream.of(fixedCost, recursiveCost, tokenCost, semanticCost).forEach(cost -> System.out.printf(Locale.ROOT,
                "%-10s %8d %11.1f %13d %15d %7.1f %9.1f%n",
                cost.strategy, cost.chunks, (double) cost.chunks / documents.size(), cost.splitEmbeds,
                cost.ingestionEmbeds(), cost.ratioTo(recursiveCost), cost.medianMillis()));

        assertThat(documents).isNotEmpty();
        assertThat(fixedCost.chunks).isPositive();
        assertThat(tokenCost.chunks).isPositive();
        assertThat(semanticCost.chunks).isPositive();
        assertThat(semanticCost.splitEmbeds).isPositive();
        assertThat(semanticCost.ratioTo(recursiveCost)).isGreaterThan(2.0);
    }

    /**
     * One pass to warm up, then {@value #MEASURED_PASSES} timed passes; the
     * reported numbers come from the last pass, the time is the median.
     */
    private static Cost measure(String strategy, List<Document> documents,
                                Function<EmbeddingModelPort, TextSplitter> factory) {
        var durations = new ArrayList<Long>();
        var chunks = 0;
        var embeds = 0;
        for (int pass = 0; pass <= MEASURED_PASSES; pass++) {
            var embeddings = new HashingEmbeddingModel();
            var splitter = factory.apply(embeddings);
            var started = System.nanoTime();
            var passChunks = 0;
            for (var document : documents) {
                passChunks += splitter.split(document).size();
            }
            durations.add(System.nanoTime() - started);
            chunks = passChunks;
            embeds = embeddings.calls.get();
        }
        durations.remove(0);
        durations.sort(null);
        return new Cost(strategy, chunks, embeds, durations.get(durations.size() / 2));
    }

    private static List<Document> corpus() {
        assertThat(Files.isDirectory(CORPUS)).as("documentation corpus at %s", CORPUS.toAbsolutePath()).isTrue();
        try (var paths = Files.walk(CORPUS)) {
            return paths.filter(path -> path.toString().endsWith(".md"))
                    .sorted()
                    .map(ChunkingCostComparisonTest::read)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Document read(Path path) {
        try {
            return new Document(path.getFileName().toString(), Files.readString(path), Map.of());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Cost(String strategy, int chunks, int splitEmbeds, long medianNanos) {

        long ingestionEmbeds() {
            return (long) chunks + splitEmbeds;
        }

        double ratioTo(Cost recursive) {
            return (double) ingestionEmbeds() / recursive.ingestionEmbeds();
        }

        double medianMillis() {
            return medianNanos / 1_000_000.0;
        }
    }

    /** Local stand-in for a provider: hashes tokens into a fixed vector and counts the calls. */
    private static final class HashingEmbeddingModel implements EmbeddingModelPort {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<Float> embed(String text) {
            calls.incrementAndGet();
            var vector = new float[VECTOR_DIMENSIONS];
            for (var token : TOKEN_BOUNDARY.split(text.toLowerCase(Locale.ROOT))) {
                if (!token.isEmpty()) {
                    vector[Math.floorMod(token.hashCode(), VECTOR_DIMENSIONS)] += 1f;
                }
            }
            var result = new ArrayList<Float>(VECTOR_DIMENSIONS);
            for (var value : vector) {
                result.add(value);
            }
            return result;
        }
    }
}
