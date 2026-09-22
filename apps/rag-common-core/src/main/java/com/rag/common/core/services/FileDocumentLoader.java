package com.rag.common.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_HASH;
import static com.rag.common.core.domain.MetadataKeys.FILE_NAME;
import static com.rag.common.core.domain.MetadataKeys.SOURCE;
import static com.rag.common.core.domain.MetadataKeys.SOURCE_TYPE;

/**
 * Reads a local file's raw bytes and descriptive metadata for sending to the
 * active rag-module, where {@link DocumentParser} (e.g. Tika) performs the
 * extraction. The bytes are streamed via multipart upload, never base64-encoded.
 */
public class FileDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(FileDocumentLoader.class);

    private static final String SHA_256 = "SHA-256";

    public LoadedFile load(String path) {
        return loadFile(Path.of(path), path);
    }

    /**
     * Loads every regular, non-hidden file under the given directory
     * (recursively), preserving each file's path relative to the root in its
     * {@code source} metadata.
     *
     * @param root the directory to walk
     * @return one {@link LoadedFile} per ingestible file (empty if none)
     */
    public List<LoadedFile> loadFolder(String root) {
        var rootPath = Path.of(root);
        if (!Files.isDirectory(rootPath)) {
            throw new DocumentLoadException("Not a directory: " + root, null);
        }
        try (var stream = Files.walk(rootPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .map(p -> loadFile(p, rootPath.relativize(p).toString()))
                    .toList();
        } catch (IOException e) {
            throw new DocumentLoadException("Failed to read folder: " + root, e);
        }
    }

    static String contentHash(byte[] bytes) {
        return sha256Hex(bytes);
    }

    private LoadedFile loadFile(Path file, String source) {
        try {
            var bytes = Files.readAllBytes(file);
            log.info("Loaded file '{}' ({} bytes)", file.getFileName(), bytes.length);
            return new LoadedFile(bytes, Map.of(
                    SOURCE_TYPE, "file",
                    SOURCE, source,
                    FILE_NAME, file.getFileName().toString(),
                    CONTENT_HASH, sha256Hex(bytes)));
        } catch (IOException e) {
            throw new DocumentLoadException("Failed to read file: " + source, e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(SHA_256).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new DocumentLoadException("SHA-256 digest not available on this JVM", e);
        }
    }

    public record LoadedFile(byte[] bytes, Map<String, Object> metadata) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof LoadedFile(byte[] thatBytes, Map<String, Object> thatMetadata)) {
                return Arrays.equals(bytes, thatBytes) && metadata.equals(thatMetadata);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(bytes);
            result = 31 * result + metadata.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "LoadedFile[bytes=" + Arrays.toString(bytes) + ", metadata=" + metadata + "]";
        }
    }

    public static class DocumentLoadException extends RuntimeException {
        public DocumentLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
