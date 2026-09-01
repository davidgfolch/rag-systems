package com.rag.tui.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileDocumentLoaderTest {

    private final FileDocumentLoader sut = new FileDocumentLoader();

    @Test
    void loadsFileContentWithMetadata(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("note.txt");
        Files.writeString(file, "hello rag");

        FileDocumentLoader.LoadedFile loaded = sut.load(file.toString());

        assertThat(loaded.content()).isEqualTo("hello rag");
        assertThat(loaded.metadata()).containsEntry("sourceType", "file");
        assertThat(loaded.metadata()).containsEntry("fileName", "note.txt");
    }

    @Test
    void throwsWhenFileMissing() {
        assertThatThrownBy(() -> sut.load("C:/does/not/exist.txt"))
                .isInstanceOf(FileDocumentLoader.DocumentLoadException.class);
    }
}