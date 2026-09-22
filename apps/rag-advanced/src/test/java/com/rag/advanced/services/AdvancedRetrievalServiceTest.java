package com.rag.advanced.services;

import com.rag.advanced.retrieval.LexicalScorer;
import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.repositories.VectorStorePort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvancedRetrievalServiceTest {

    private static final int CANDIDATE_COUNT_FOR_TOP_5 = 20;

    private final VectorStorePort vectorStore = mock(VectorStorePort.class);
    private final AdvancedRetrievalService service = new AdvancedRetrievalService(vectorStore, new LexicalScorer());

    @Test
    void returnsCandidatesWhenNotEnoughToRerank() {
        var result = new Chunk("c1", "d1", "content", 0, Map.of());
        when(vectorStore.similaritySearch("query", CANDIDATE_COUNT_FOR_TOP_5)).thenReturn(List.of(result));
        assertThat(service.retrieve("query", 5)).containsExactly(result);
    }

    @Test
    void returnsEmptyWhenNoMatches() {
        when(vectorStore.similaritySearch("query", CANDIDATE_COUNT_FOR_TOP_5)).thenReturn(List.of());
        assertThat(service.retrieve("query", 5)).isEmpty();
    }

    @Test
    void delegatesDocumentScopedQuery() {
        when(vectorStore.similaritySearch("query", CANDIDATE_COUNT_FOR_TOP_5, "d1")).thenReturn(List.of());
        assertThat(service.retrieve("query", 5, "d1")).isEmpty();
    }

    @Test
    void appliesMetadataFilterBeforeReranking() {
        var match = new Chunk("c1", "d1", "web target", 0, Map.of("source", "web"));
        var other = new Chunk("c2", "d1", "file only", 1, Map.of("source", "file"));
        when(vectorStore.similaritySearch("query", CANDIDATE_COUNT_FOR_TOP_5)).thenReturn(List.of(match, other));
        assertThat(service.retrieve("query", 5, Map.of("source", "web"))).containsExactly(match);
    }

    @Test
    void promotesSecondVectorCandidateWithStrongLexicalMatch() {
        var vectorBest = new Chunk("a", "d1", "unrelated filler without the query word", 0, Map.of());
        var lexicalBest = new Chunk("b", "d1", "conductor conductor conductor conductor", 1, Map.of());
        var weak = new Chunk("c", "d1", "conductor", 2, Map.of());
        var weaker = new Chunk("d", "d1", "conductor", 3, Map.of());
        when(vectorStore.similaritySearch("conductor", 4)).thenReturn(List.of(vectorBest, lexicalBest, weak, weaker));
        assertThat(service.retrieve("conductor", 1)).extracting(Chunk::getId).containsExactly("b");
    }

    @Test
    void keepsVectorOrderWhenLexicalSignalIsTiedToVectorRank() {
        var first = new Chunk("a", "d1", "conductor", 0, Map.of());
        var second = new Chunk("b", "d1", "noise noise noise", 1, Map.of());
        when(vectorStore.similaritySearch("conductor", 8)).thenReturn(List.of(first, second));
        assertThat(service.retrieve("conductor", 2)).extracting(Chunk::getId).containsExactly("a", "b");
    }

    @Test
    void delegatesDeleteToVectorStore() {
        service.delete("d1");
        verify(vectorStore).delete("d1");
    }

    @Test
    void listsDocumentsViaVectorStore() {
        when(vectorStore.listDocuments()).thenReturn(List.of(new DocumentSummary("d1", 2, Map.of())));
        assertThat(service.listDocuments()).hasSize(1);
    }
}