package com.rag.common.repositories.store;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.DocumentSummary;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

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

    private final org.springframework.ai.vectorstore.VectorStore delegate =
            mock(org.springframework.ai.vectorstore.VectorStore.class);
    private final PgVectorStoreAdapter adapter = new PgVectorStoreAdapter(delegate);

    @Test
    void addsChunksAsSpringDocuments() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "test");
        Chunk chunk = new Chunk("c1", "d1", "content", 3, meta);

        adapter.add(List.of(chunk));

        verify(delegate).add(any());
    }

    @Test
    void convertsSearchResultsToChunks() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("documentId", "d1");
        meta.put("chunkIndex", 2);
        Document springDoc = new Document.Builder()
                .id("c1")
                .text("retrieved content")
                .metadata(meta)
                .build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5);

        assertThat(result).hasSize(1);
        Chunk c = result.get(0);
        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.getDocumentId()).isEqualTo("d1");
        assertThat(c.getIndex()).isEqualTo(2);
        assertThat(c.getContent()).isEqualTo("retrieved content");
    }

    @Test
    void handlesMissingMetadataOnReturnedChunk() {
        Document springDoc = new Document.Builder().id("c1").text("text").build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDocumentId()).isEqualTo("unknown");
        assertThat(result.get(0).getIndex()).isZero();
    }

    @Test
    void scopesSearchByDocumentIdWithFilter() {
        Document springDoc = new Document.Builder().id("c1").text("text").build();
        when(delegate.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(springDoc));

        List<Chunk> result = adapter.similaritySearch("query", 5, "d1");

        assertThat(result).hasSize(1);
    }

    @Test
    void listsDocumentsFromQualifiedPgTable() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("document_id")).thenReturn("d1", "d2");
        when(rs.getInt("chunk_count")).thenReturn(3, 1);
        when(rs.getString("first_meta")).thenReturn("{\"fileName\":\"note.txt\"}", null);

        PgVectorStoreAdapter pgAdapter = new PgVectorStoreAdapter(delegate, dataSource, "rag_basic", "chunks");
        List<DocumentSummary> documents = pgAdapter.listDocuments();

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).documentId()).isEqualTo("d1");
        assertThat(documents.get(0).chunkCount()).isEqualTo(3);
        assertThat(documents.get(0).metadata()).containsEntry("fileName", "note.txt");
        assertThat(documents.get(1).documentId()).isEqualTo("d2");
        assertThat(documents.get(1).metadata()).isEmpty();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(captor.capture());
        assertThat(captor.getValue()).contains("\"rag_basic\".\"chunks\"");
    }

    @Test
    void treatsMissingTableAsNoDocuments() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException missing = mock(SQLException.class);
        when(missing.getSQLState()).thenReturn("42P01");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(missing);

        PgVectorStoreAdapter pgAdapter = new PgVectorStoreAdapter(delegate, dataSource, "rag_basic", "chunks");

        assertThat(pgAdapter.listDocuments()).isEmpty();
    }

    @Test
    void listsNoDocumentsWhenNoDataSourceConfigured() {
        assertThat(adapter.listDocuments()).isEmpty();
    }
}