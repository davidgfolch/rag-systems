package com.rag.tui.launcher;

/**
 * A runnable rag-* module: its logical name and base HTTP URL.
 */
public record Module(String name, String baseUrl) {

    public String wsUrl() {
        return baseUrl.replaceFirst("^http", "ws") + "/ws/chat";
    }
}