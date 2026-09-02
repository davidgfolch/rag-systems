package com.rag.common.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for rag-common. Domain contains only pure, dependency-free
 * models; services and repositories may depend on domain but not on each other.
 * Also enforces repository hygiene (minimal root, docs/scripts placement, portability).
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.common";

    private static final Set<String> ALLOWED_ROOT_ENTRIES = Set.of(
            ".claude", ".github", ".git", ".idea", ".mvn", ".opencode",
            "apps", "docs", "docker", "scripts", "target",
            ".dockerignore", ".env.example", ".gitignore",
            "mvnw", "mvnw.cmd", "pom.xml", "README.md", "sonar-project.properties");

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void layersRespectDependencyFlow() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy("..domain..")
                .layer("services").definedBy("..services..")
                .layer("repositories").definedBy("..repositories..")
                .whereLayer("domain").mayNotAccessAnyLayer()
                .whereLayer("services").mayOnlyAccessLayers("domain", "repositories")
                .whereLayer("repositories").mayOnlyAccessLayers("domain", "services")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void domainDependsOnNothing() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..services..", "..repositories..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void repoRootContainsOnlyAllowedEntries() throws IOException {
        List<String> unexpected = listRoot().stream()
                .filter(name -> !ALLOWED_ROOT_ENTRIES.contains(name))
                .toList();
        assertTrue(unexpected.isEmpty(),
                "Keep the repo root minimal (README + badges show on the GitHub landing page): " + unexpected);
    }

    @Test
    void noDocsOrScriptsAtRoot() throws IOException {
        List<String> offending = listRoot().stream()
                .filter(name -> name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".ps1")
                        || (name.endsWith(".md") && !name.equals("README.md")))
                .toList();
        assertTrue(offending.isEmpty(),
                "Docs belong in docs/ and scripts in scripts/; only README.md is allowed at root: " + offending);
    }

    @Test
    void scriptsArePortable() throws IOException {
        Path scripts = repoRoot().resolve("scripts");
        List<String> problems = new ArrayList<>();
        for (String base : scriptBasenames(scripts, ".sh")) {
            if (!Files.exists(scripts.resolve(base + ".bat")) && !Files.exists(scripts.resolve(base + ".ps1"))) {
                problems.add(base + " has no Windows variant (.bat/.ps1)");
            }
        }
        for (String base : scriptBasenames(scripts, ".bat")) {
            if (!Files.exists(scripts.resolve(base + ".sh"))) {
                problems.add(base + " has no Unix variant (.sh)");
            }
        }
        assertTrue(problems.isEmpty(),
                "Every script needs a .sh + .bat/.ps1 pair (project must stay portable): " + problems);
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

    private static List<String> listRoot() throws IOException {
        try (Stream<Path> entries = Files.list(repoRoot())) {
            return entries.map(p -> p.getFileName().toString()).toList();
        }
    }

    private static Set<String> scriptBasenames(Path dir, String ext) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(ext))
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void everyRunnableModuleHasApplicationContextTest() throws IOException {
        List<String> missing = new ArrayList<>();
        Path apps = repoRoot().resolve("apps");
        if (!Files.isDirectory(apps)) {
            return;
        }
        try (Stream<Path> modules = Files.list(apps)) {
            var moduleEntries = modules.filter(Files::isDirectory).toList();
            for (Path module : moduleEntries) {
                List<Path> appFiles = findFile(module.resolve("src").resolve("main"),
                        name -> name.endsWith("Application.java"));
                for (Path app : appFiles) {
                    String appName = app.getFileName().toString().replace(".java", "");
                    String expectedTest = appName.replace("Application", "ApplicationContextTest") + ".java";
                    if (findFile(module.resolve("src").resolve("test"), name -> name.equals(expectedTest)).isEmpty()) {
                        missing.add(appName + " -> " + expectedTest);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "every runnable module must boot via a @SpringBootTest context test "
                        + "(<Module>ApplicationContextTest) to prove wiring loads offline: " + missing);
    }

    private static List<Path> findFile(Path base, java.util.function.Predicate<String> matcher) throws IOException {
        List<Path> matches = new ArrayList<>();
        if (base == null || !Files.isDirectory(base)) {
            return matches;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> matcher.test(p.getFileName().toString()))
                    .forEach(matches::add);
        }
        return matches;
    }
}