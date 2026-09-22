package com.rag.common.core.architecture;

import com.rag.common.core.testfixture.TestRepoPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-module file-length gate. Walks every .java file under the src trees of
 * all modules in apps (generated sources under target are excluded) and,
 * before asserting, prints a unified, color-coded report of every file that
 * violates the 200-line guideline: WARNING (>=200, yellow), ALERT (>=300,
 * orange), FAULT (>=400, red). Any ALERT or FAULT fails the test, so oversized
 * files are always shown in full before the failure message.
 */
class FileLengthArchitectureTest {

    private static final int WARN_LINES = 200;
    private static final int ALERT_LINES = 300;
    private static final int FAULT_LINES = 400;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_ORANGE = "\u001B[38;5;208m";
    private static final String ANSI_RED = "\u001B[31m";

    private static final String JAVA_SUFFIX = ".java";
    private static final String TARGET_DIR = "target";

    record FileLength(int lines, String path) {
    }

    @Test
    void noFileReachesAlertThreshold() throws IOException {
        List<FileLength> all = scan();
        List<FileLength> report = all.stream()
                .filter(v -> v.lines >= WARN_LINES)
                .sorted(Comparator.comparingInt(FileLength::lines).reversed()
                        .thenComparing(FileLength::path))
                .toList();
        print(report, all);
        List<String> failing = report.stream()
                .filter(v -> v.lines >= ALERT_LINES)
                .map(v -> v.path + " (" + v.lines + " lines)")
                .toList();
        assertThat(failing)
                .as("files with >= %d lines (ALERT/FAULT) must be split; the full report is printed above",
                        ALERT_LINES)
                .isEmpty();
    }

    private void print(List<FileLength> report, List<FileLength> all) {
        if (report.isEmpty()) {
            System.out.println("File length: no files above " + WARN_LINES + " lines.");
            return;
        }
        long failures = report.stream().filter(v -> v.lines >= ALERT_LINES).count();
        System.out.println("File length report (WARNING >= " + WARN_LINES + ", ALERT >= " + ALERT_LINES
                + " fails, FAULT >= " + FAULT_LINES + "): "
                + failures + " of " + all.size() + " java files fail");
        report.forEach(v -> System.out.println("  " + render(v)));
    }

    private static String render(FileLength v) {
        return color(v) + label(v) + ANSI_RESET + "  " + v.lines + " lines  " + v.path;
    }

    private static String color(FileLength v) {
        if (v.lines >= FAULT_LINES) return ANSI_RED;
        if (v.lines >= ALERT_LINES) return ANSI_ORANGE;
        return ANSI_YELLOW;
    }

    private static String label(FileLength v) {
        if (v.lines >= FAULT_LINES) return "FAULT  ";
        if (v.lines >= ALERT_LINES) return "ALERT  ";
        return "WARNING";
    }

    private static List<FileLength> scan() throws IOException {
        List<FileLength> files = new ArrayList<>();
        try (Stream<Path> modules = Files.list(TestRepoPaths.root().resolve("apps"))) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                scanModule(module, files);
            }
        }
        return files;
    }

    private static void scanModule(Path module, List<FileLength> out) throws IOException {
        try (Stream<Path> walk = Files.walk(module)) {
            List<Path> javaFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(JAVA_SUFFIX))
                    .filter(p -> !hasTargetSegment(p))
                    .toList();
            for (Path p : javaFiles) {
                out.add(new FileLength(countLines(p), relativePath(p)));
            }
        }
    }

    private static boolean hasTargetSegment(Path p) {
        for (int i = 0; i < p.getNameCount(); i++) {
            if (TARGET_DIR.equals(p.getName(i).toString())) return true;
        }
        return false;
    }

    private static int countLines(Path p) throws IOException {
        return (int) Files.lines(p).count();
    }

    private static String relativePath(Path p) {
        return TestRepoPaths.root().relativize(p).toString().replace('\\', '/');
    }
}