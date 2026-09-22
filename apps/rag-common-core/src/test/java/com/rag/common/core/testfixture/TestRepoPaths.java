package com.rag.common.core.testfixture;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the monorepo root for repo-wide architecture/hygiene checks
 * (minimal root, portability, cross-module file-length gate).
 */
public final class TestRepoPaths {

    private TestRepoPaths() {}

    public static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !isRepoRoot(dir)) {
            dir = dir.getParent();
        }
        return dir;
    }

    private static boolean isRepoRoot(Path dir) {
        return Files.isDirectory(dir.resolve("scripts"))
                && Files.isDirectory(dir.resolve("apps"))
                && Files.isDirectory(dir.resolve("docs"))
                && Files.isRegularFile(dir.resolve("README.md"));
    }
}