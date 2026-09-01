package com.rag.webcrawler.services.ranking;

import java.util.List;

/**
 * Strategy interface for ranking which outbound links are worth loading,
 * given an optional user question.
 */
public interface LinkPrioritizer {

    List<String> prioritize(List<String> links, String question);
}