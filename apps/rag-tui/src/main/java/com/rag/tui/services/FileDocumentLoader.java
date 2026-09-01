package com.rag.tui.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads a local file's raw text (no parsing/RAG logic) for sending to the
 * active rag-module, which is the one owning document processing.
 */
public class FileDocumentLoader {

    public LoadedFile load(String path) {
        try {
            Path p = Path.of(path);
            String content = Files.readString(p);
            Map<String, Object> metadata = Map.of(
                    "sourceType", "file",
                    "source", path,
                    "fileName", p.getFileName().toString());
            return new LoadedFile(content, metadata);
        } catch (IOException e) {
            throw new DocumentLoadException("Failed to read file: " + path, e);
        }
    }

    public record LoadedFile(String content, Map<String, Object> metadata) {}

    public static class DocumentLoadException extends RuntimeException {
        public DocumentLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}