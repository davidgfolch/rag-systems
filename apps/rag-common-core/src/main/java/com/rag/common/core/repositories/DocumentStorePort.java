package com.rag.common.core.repositories;

import com.rag.common.core.domain.DocumentSummary;

import java.util.List;

/**
 * Client-side port for listing and deleting ingested documents. Implemented by
 * each module's retrieval service so the shared {@code IngestionController} can
 * drive document management without depending on a module-specific service.
 */
public interface DocumentStorePort {

    List<DocumentSummary> listDocuments();

    void delete(String documentId);
}