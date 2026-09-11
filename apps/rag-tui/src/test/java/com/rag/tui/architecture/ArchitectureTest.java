package com.rag.tui.architecture;

import com.rag.tui.config.RagTuiConfig;
import com.rag.tui.ui.CommandDispatcher;
import com.rag.tui.ui.TerminalStyle;
import com.rag.common.services.FileDocumentLoader;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture rules for the thin rag-tui, enforced by ArchUnit. The TUI is a
 * control plane: launcher owns process/registry logic, clients talk to modules
 * over REST/WS, and ui routes commands. No RAG logic. The sole shared rag-common
 * dependency is {@link FileDocumentLoader} (file I/O only, not RAG internals).
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.tui";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void layersMayOnlyDependInward() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("ui").definedBy("..ui..")
                .layer("launcher").definedBy("..launcher..")
                .layer("client").definedBy("..client..")
                .whereLayer("ui").mayOnlyAccessLayers("launcher", "client")
                .whereLayer("client").mayOnlyAccessLayers("launcher")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void noDependencyOnCommonRagInternals() {
        noClasses()
                .that().resideInAPackage(ROOT)
                .and().doNotBelongToAnyOf(CommandDispatcher.class, RagTuiConfig.class)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rag.common.services", "com.rag.common.repositories",
                        "com.rag.common.domain", "com.rag.common.adapter")
                .as("no TUI class may depend on rag-common RAG internals "
                        + "(except CommandDispatcher/RagTuiConfig, pinned to the shared document loader)")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void uiDoesNotDependOnConfig() {
        noClasses()
                .that().resideInAPackage("..ui..")
                .should().dependOnClassesThat().resideInAPackage("..config..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void everyClientAndUiClassHasIntegrationTest() {
        Set<String> covered = integrationTestNames();
        List<String> missing = importer.importPackages(ROOT).stream()
                .filter(c -> (c.getPackageName().contains(".client") || c.getPackageName().contains(".ui"))
                        && !c.isNestedClass() && !c.isInterface() && !c.isRecord()
                        && !c.getSimpleName().isEmpty()
                        && !c.getFullName().equals(TerminalStyle.class.getName())
                        && !c.getSimpleName().equals("ShellExitException"))
                .map(JavaClass::getSimpleName)
                .filter(name -> !covered.contains(name))
                .sorted()
                .toList();
        assertThat(missing)
                .as("every REST/WS/terminal boundary class must be exercised by a real-socket "
                        + "integration test (<Class>IntegrationTest), not only mocked unit tests")
                .isEmpty();
    }

    @Test
    void testFixturesShouldStartWithTestPrefix() {
        var allClasses = new ClassFileImporter().importPackages(ROOT);
        classes().that().resideInAPackage("..testfixture..")
                .should().haveSimpleNameStartingWith("Test")
                .check(allClasses);
    }

    @Test
    void testFixturesShouldBeUnder100Lines() {
        var allClasses = new ClassFileImporter().importPackages(ROOT);
        List<String> tooLong = allClasses.stream()
                .filter(c -> c.getPackageName().contains(".testfixture"))
                .map(c -> {
                    try {
                        int lines = countSourceLines(c);
                        return lines > 100
                                ? c.getSimpleName() + " (" + lines + " lines > 100)"
                                : null;
                    } catch (IOException _) {
                        return c.getSimpleName() + " (cannot read source)";
                    }
                })
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        assertThat(tooLong)
                .as("test fixtures should stay under 100 lines to remain focused")
                .isEmpty();
    }

    private static int countSourceLines(JavaClass clazz) throws IOException {
        String pkg = clazz.getPackageName();
        String[] parts = pkg.split("\\.");
        String module = "rag-" + parts[2];
        String relPath = pkg.replace('.', '/') + "/" + clazz.getSimpleName() + ".java";
        Path source = repoRoot().resolve("apps").resolve(module)
                .resolve("src").resolve("test").resolve("java").resolve(relPath);
        return (int) Files.lines(source).count();
    }

    private static Path repoRoot() {
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

    private static Set<String> integrationTestNames() {
        return new ClassFileImporter().importPackages(ROOT).stream()
                .filter(c -> c.getSimpleName().endsWith("IntegrationTest"))
                .map(c -> c.getSimpleName().substring(0, c.getSimpleName().length() - "IntegrationTest".length()))
                .collect(Collectors.toSet());
    }
}