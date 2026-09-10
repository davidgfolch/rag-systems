package com.rag.tui.ui;

import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;

public class DocumentLister {

    public static final String NO_DOCUMENTS = "No rag-* modules contains documents";
    public static final String RAG_MODULES_DOWN = "No rag-* modules are reachable. Start one first (e.g. 'start rag-basic').";

    private final ModuleRegistry registry;
    private final RagApiClient apiClient;
    private final ModuleHealthClient healthClient;

    public DocumentLister(ModuleRegistry registry, RagApiClient apiClient, ModuleHealthClient healthClient) {
        this.registry = registry;
        this.apiClient = apiClient;
        this.healthClient = healthClient;
    }

    public String list() {
        var sb = new StringBuilder("Documents:\n");
        boolean anyModule = false;
        boolean anyDoc = false;
        for (Module m : registry.modules()) {
            if (healthClient.isUp(m.baseUrl())) {
                anyModule = true;
                anyDoc |= appendModuleDocuments(sb, m);
            }
        }
        if (anyDoc)
            return sb.toString();
        else if (anyModule)
            return NO_DOCUMENTS;
        return RAG_MODULES_DOWN;
    }

    private boolean appendModuleDocuments(StringBuilder sb, Module m) {
        var docs = apiClient.listDocuments(m.baseUrl());
        if (docs.isEmpty()) {
            return false;
        }
        sb.append(" ").append(m.name()).append(" (").append(m.baseUrl()).append("):\n");
        int maxChunksLength = docs.stream().map(DocumentSummaryDTO::getChunkCount).max(Integer::compareTo)
                .map(String::valueOf).map(String::length).orElse(0);
        for (DocumentSummaryDTO doc : docs) {
            appendDocumentSummary(sb, doc, maxChunksLength);
        }
        return true;
    }

    private static void appendDocumentSummary(StringBuilder sb, DocumentSummaryDTO doc, int maxChunksLength) {
        sb.append("   - ");
        if (doc.getChunkCount() != null) {
            var chunkCount = String.valueOf(doc.getChunkCount());
            sb.append(" (")
                    .append(" ".repeat(Math.max(0, maxChunksLength - chunkCount.length())))
                    .append(chunkCount)
                    .append(" chunks)");
        }
        sb.append(String.format(" [%s]", doc.getDocumentId()));
        if (doc.getCreatedAt() != null)
            sb.append(" ").append(doc.getCreatedAt());
        sb.append(" ").append(doc.getTitle());
        sb.append("\n");
    }
}
