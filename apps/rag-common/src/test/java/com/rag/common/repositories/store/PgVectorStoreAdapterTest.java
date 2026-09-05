package com.rag.common.repositories.store;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.DocumentSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorStoreAdapterTest {

    private final VectorStore delegate = mock(VectorStore.class);
    private final PgVectorStoreAdapter adapter = new PgVectorStoreAdapter(delegate);

    @Test
    void addsChunksAsSpringDocuments() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "test");
        var chunk = new Chunk("c1", "d1", "content", 3, meta);

        adapter.add(List.of(chunk));

        verify(delegate).add(any());
    }

    @Test
    void convertsSearchResultsToChunks() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("documentId", "d1");
        meta.put("chunkIndex", 2);
        var springDoc = new Document.Builder()
                .id("c1")
                .text("retrieved content")
                .metadata(meta)
                .build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5);

        assertThat(result).hasSize(1);
        Chunk c = result.getFirst();
        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.getDocumentId()).isEqualTo("d1");
        assertThat(c.getIndex()).isEqualTo(2);
        assertThat(c.getContent()).isEqualTo("retrieved content");
    }

    @Test
    void handlesMissingMetadataOnReturnedChunk() {
        var springDoc = new Document.Builder().id("c1").text("text").build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getDocumentId()).isEqualTo("unknown");
        assertThat(result.getFirst().getIndex()).isZero();
    }

    @Test
    void scopesSearchByDocumentIdWithFilter() {
        var springDoc = new Document.Builder().id("c1").text("text").build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5, "d1");

        assertThat(result).hasSize(1);
    }

    @Test
    void listsDocumentsFromQualifiedPgTable() throws Exception {
        var ds = mock(DataSource.class);
        var conn = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("document_id")).thenReturn("d1", "d2");
        when(rs.getInt("chunk_count")).thenReturn(3, 1);
        when(rs.getString("first_meta")).thenReturn("{\"fileName\":\"note.txt\"}", (String)null);

        var pgAdapter = new PgVectorStoreAdapter(delegate, ds, "rag_basic", "chunks");
        List<DocumentSummary> docs = pgAdapter.listDocuments();

        assertThat(docs).hasSize(2);
        assertThat(docs.getFirst().documentId()).isEqualTo("d1");
        assertThat(docs.get(0).chunkCount()).isEqualTo(3);
        assertThat(docs.get(0).metadata()).containsEntry("fileName", "note.txt");
        assertThat(docs.get(1).documentId()).isEqualTo("d2");
        assertThat(docs.get(1).metadata()).isEmpty();

        var captor = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(captor.capture());
        assertThat(captor.getValue()).contains("\"rag_basic\".\"chunks\"");
    }

    @Test
    void treatsMissingTableAsNoDocuments() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException missing = mock(SQLException.class);
        when(missing.getSQLState()).thenReturn("42P01");

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(missing);

        var pgAdapter = new PgVectorStoreAdapter(delegate, ds, "rag_basic", "chunks");

        assertThat(pgAdapter.listDocuments()).isEmpty();
    }

    @Test
    void listsNoDocumentsWhenNoDataSourceConfigured() {
        assertThat(adapter.listDocuments()).isEmpty();
    }
}