package com.rag.common.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Loads a local file's raw bytes, base64-encoded, for sending to the active
 * rag-module where {@link DocumentParser} (e.g. Tika) performs the extraction.
 * Binary formats (PDF, DOCX, XLSX) are carried intact rather than decoded as text.
 */
public class FileDocumentLoader {

    public LoadedFile load(String path) {
        try {
            Path p = Path.of(path);
            byte[] bytes = Files.readAllBytes(p);
            return new LoadedFile("", Map.of(
                    "sourceType", "file",
                    "source", path,
                    "fileName", p.getFileName().toString(),
                    "raw", Base64.getEncoder().encodeToString(bytes)));
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
