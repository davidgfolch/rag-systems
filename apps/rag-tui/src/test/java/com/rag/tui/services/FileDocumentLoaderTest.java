package com.rag.tui.services;

import com.rag.common.domain.Document;
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
    void loadsFileIntoDocumentWithMetadata(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("note.txt");
        Files.writeString(file, "hello rag");

        Document doc = sut.load(file.toString());

        assertThat(doc.getContent()).isEqualTo("hello rag");
        assertThat(doc.getMetadata()).containsEntry("sourceType", "file");
        assertThat(doc.getId()).startsWith("file-");
    }

    @Test
    void throwsWhenFileMissing() {
        assertThatThrownBy(() -> sut.load("C:/does/not/exist.txt"))
                .isInstanceOf(FileDocumentLoader.DocumentLoadException.class);
    }
}
