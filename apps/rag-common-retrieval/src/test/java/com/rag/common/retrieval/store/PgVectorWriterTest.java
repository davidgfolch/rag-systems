package com.rag.common.retrieval.store;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PgVectorWriterTest {

    private static final String TABLE = "\"s\".\"t\"";
    private static final String INDEX_NAME = "idx";
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final Statement indexStatement = mock(Statement.class);

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.createStatement()).thenReturn(indexStatement);
    }

    @Test
    void writesPrecomputedEmbeddingsAndEnsuresTheDocumentIndexOnce() throws Exception {
        var sut = writer();
        sut.add(List.of(chunk()));
        sut.add(List.of(chunk()));
        verify(indexStatement, times(1)).execute(contains("CREATE INDEX IF NOT EXISTS"));
        verify(statement, times(2)).executeBatch();
        verify(statement, times(2)).addBatch();
    }

    @Test
    void splitsLargeWritesIntoBatches() throws Exception {
        var chunks = new ArrayList<Chunk>();
        for (int i = 0; i < 501; i++) {
            chunks.add(chunk());
        }
        writer().add(chunks);
        verify(statement, times(2)).executeBatch();
        verify(statement, times(501)).addBatch();
    }

    @Test
    void skipsWritesForAnEmptyBatch() {
        writer().add(List.of());
        verifyNoInteractions(dataSource);
    }

    @Test
    void rejectsChunksWithoutAnEmbedding() {
        var sut = writer();
        var withoutEmbedding = new Chunk(UUID.randomUUID().toString(), "d1", "text", 0, Map.of());
        var batch = List.of(withoutEmbedding);
        assertThatThrownBy(() -> sut.add(batch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no embedding");
    }

    private PgVectorWriter writer() {
        return new PgVectorWriter(dataSource, TABLE, INDEX_NAME, RetryPolicy.noRetry(),
                QUERY_TIMEOUT_SECONDS, RetryObserver.NOOP);
    }

    private static Chunk chunk() {
        var chunk = new Chunk(UUID.randomUUID().toString(), "d1", "text", 0, Map.of());
        chunk.setEmbedding(new float[]{1f, 0f});
        return chunk;
    }
}
