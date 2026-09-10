package com.rag.common.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rag.common.domain.MetadataKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
        assertThat(loaded.metadata()).containsEntry(MetadataKeys.SOURCE_TYPE, "file");
        assertThat(loaded.metadata()).containsEntry(MetadataKeys.FILE_NAME, "note.txt");
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

    @Test
    void loadsAllFilesRecursivelyWithRelativeSources(@TempDir Path dir) throws IOException {
        Path sub = dir.resolve("sub");
        Files.createDirectories(sub);
        Files.write(dir.resolve("a.txt"), "a".getBytes(StandardCharsets.UTF_8));
        Files.write(sub.resolve("b.md"), "b".getBytes(StandardCharsets.UTF_8));
        var loaded = sut.loadFolder(dir.toString());
        assertThat(loaded).hasSize(2);
        assertThat(loaded).extracting(f -> f.metadata().get(MetadataKeys.FILE_NAME))
                .containsExactlyInAnyOrder("a.txt", "b.md");
        assertThat(loaded).extracting(f -> normalize(f.metadata().get(MetadataKeys.SOURCE).toString()))
                .containsExactlyInAnyOrder("a.txt", "sub/b.md");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    @Test
    void loadFolderSkipsHiddenFilesAndReturnsEmptyWhenOnlyHidden(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve(".hidden.txt"), "x".getBytes(StandardCharsets.UTF_8));
        var loaded = sut.loadFolder(dir.toString());
        assertThat(loaded).isEmpty();
    }

    @Test
    void loadFolderThrowsWhenPathIsNotADirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("note.txt");
        Files.write(file, "hi".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> sut.loadFolder(file.toString()))
                .isInstanceOf(FileDocumentLoader.DocumentLoadException.class);
    }

    @Test
    void loadFolderThrowsWhenDirectoryMissing() {
        assertThatThrownBy(() -> sut.loadFolder("C:/does/not/exist"))
                .isInstanceOf(FileDocumentLoader.DocumentLoadException.class);
    }

    @Test
    void loadedFileEqualsWhenBytesAndMetadataMatch() {
        FileDocumentLoader.LoadedFile a = new FileDocumentLoader.LoadedFile(new byte[]{1, 2}, Map.of("k", "v"));
        FileDocumentLoader.LoadedFile b = new FileDocumentLoader.LoadedFile(new byte[]{1, 2}, Map.of("k", "v"));
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void loadedFileNotEqualsWhenBytesDiffer() {
        FileDocumentLoader.LoadedFile a = new FileDocumentLoader.LoadedFile(new byte[]{1, 2}, Map.of("k", "v"));
        FileDocumentLoader.LoadedFile b = new FileDocumentLoader.LoadedFile(new byte[]{1}, Map.of("k", "v"));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void loadedFileNotEqualsWhenMetadataDiffers() {
        FileDocumentLoader.LoadedFile a = new FileDocumentLoader.LoadedFile(new byte[]{1}, Map.of("k", "v"));
        FileDocumentLoader.LoadedFile other = new FileDocumentLoader.LoadedFile(new byte[]{1}, Map.of("k", "w"));
        assertThat(a).isNotEqualTo(other);
    }

    @Test
    void loadedFileEqualsIsReflexiveAndRejectsNullAndOtherTypes() {
        FileDocumentLoader.LoadedFile a = new FileDocumentLoader.LoadedFile(new byte[]{1}, Map.of("k", "v"));
        assertThat(a.equals(null)).isFalse();
        assertThat(a.equals("not-a-loaded-file")).isFalse();
        assertThat(a).isEqualTo(a);
    }

    @Test
    void loadedFileToStringContainsBytesAndMetadata() {
        FileDocumentLoader.LoadedFile f = new FileDocumentLoader.LoadedFile(new byte[]{1, 2}, Map.of("k", "v"));
        assertThat(f.toString()).contains("[1, 2]").contains("k=v");
    }
}