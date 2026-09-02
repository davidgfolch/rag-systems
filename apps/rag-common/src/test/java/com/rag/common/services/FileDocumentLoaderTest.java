package com.rag.common.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDocumentLoaderTest {

    private final FileDocumentLoader sut = new FileDocumentLoader();

    @Test
    void loadsRawBytesWithMetadata(@TempDir Path dir) throws IOException {
        byte[] bytes = "hello rag".getBytes(StandardCharsets.UTF_8);
        Path file = dir.resolve("note.txt");
        Files.write(file, bytes);

        FileDocumentLoader.LoadedFile loaded = sut.load(file.toString());

        assertThat(loaded.bytes()).isEqualTo(bytes);
        assertThat(loaded.metadata()).containsEntry("sourceType", "file");
        assertThat(loaded.metadata()).containsEntry("fileName", "note.txt");
    }

    @Test
    void loadsBinaryBytesUnchanged(@TempDir Path dir) throws IOException {
        byte[] bytes = new byte[]{(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46, (byte) 0x00, (byte) 0xFF};
        Path file = dir.resolve("doc.pdf");
        Files.write(file, bytes);

        FileDocumentLoader.LoadedFile loaded = sut.load(file.toString());

        assertThat(loaded.bytes()).isEqualTo(bytes);
    }

    @Test
    void throwsWhenFileMissing() {
        assertThatThrownBy(() -> sut.load("C:/does/not/exist.txt"))
                .isInstanceOf(FileDocumentLoader.DocumentLoadException.class);
    }
}
