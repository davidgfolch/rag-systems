package com.rag.tui.ui;

import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;

public class DocumentLister {

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
        boolean any = false;
        for (Module m : registry.modules()) {
            if (healthClient.isUp(m.baseUrl())) {
                any = true;
                appendModuleDocuments(sb, m);
            }
        }
        return any ? sb.toString()
                : "No rag-* modules are reachable. Start one first (e.g. 'start rag-basic').";
    }

    private void appendModuleDocuments(StringBuilder sb, Module m) {
        sb.append(" ").append(m.name()).append(" (").append(m.baseUrl()).append("):\n");
        var docs = apiClient.listDocuments(m.baseUrl());
        if (docs.isEmpty()) {
            sb.append("   (no documents)\n");
            return;
        }
        int maxChunksLength = docs.stream().map(DocumentSummaryDTO::getChunkCount).max(Integer::compareTo)
                .map(String::valueOf).map(String::length).orElse(0);
        for (DocumentSummaryDTO doc : docs) {
            appendDocumentSummary(sb, doc, maxChunksLength);
        }
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
