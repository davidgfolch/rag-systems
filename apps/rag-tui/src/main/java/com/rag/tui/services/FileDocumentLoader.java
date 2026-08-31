package com.rag.tui.services;

import com.rag.common.domain.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Loads a local file into a {@link Document} with source metadata.
 */
public class FileDocumentLoader {

    public Document load(String path) {
        try {
            Path p = Path.of(path);
            String content = Files.readString(p);
            Map<String, Object> metadata = Map.of(
                    "sourceType", "file",
                    "source", path,
                    "fileName", p.getFileName().toString());
            return new Document("file-" + UUID.randomUUID(), content, metadata);
        } catch (IOException e) {
            throw new DocumentLoadException("Failed to read file: " + path, e);
        }
    }

    public static class DocumentLoadException extends RuntimeException {
        public DocumentLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
