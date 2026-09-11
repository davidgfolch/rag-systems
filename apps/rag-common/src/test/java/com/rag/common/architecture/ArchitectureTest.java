package com.rag.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

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
            ".dockerignore", ".gitignore", ".codegraph",
            ".env", ".env.secrets",
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
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..services..", "..repositories..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void everyBusinessClassHasLogger() {
        classes()
                .that().resideInAnyPackage("..services..", "..api..", "..client..",
                        "..launcher..", "..adapter..", "..repositories.store..")
                .and().areNotInterfaces()
                .and().areNotNestedClasses()
                .and().areNotRecords()
                .should(new ArchCondition<>("declare an SLF4J logger field named 'log'") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean hasLogger = item.getFields().stream()
                                .anyMatch(f -> "log".equals(f.getName())
                                        && f.getRawType().isEquivalentTo(Logger.class));
                        if (!hasLogger) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getDescription() + " has no SLF4J logger field 'log'"));
                        }
                    }
                })
                .because("structured logging is mandatory: every concrete service/controller/handler/"
                        + "client/adapter must declare a SLF4J logger field named 'log'")
                .check(importer.importPackages("com.rag"));
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

    @Test
    void metadataKeysShouldBeUsedInServicesAndAdapters() {
        var allClasses = new ClassFileImporter().importPackages("com.rag");
        Set<String> callers = allClasses.stream()
                .flatMap(c -> c.getFieldAccessesToSelf().stream())
                .map(access -> access.getOrigin().getOwner())
                .filter(c -> c.getPackageName().contains("services")
                        || c.getPackageName().contains("adapter"))
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toSet());
        assertThat(callers)
                .as("MetadataKeys constants should be used by services and adapters, "
                        + "not replaced by magic string literals")
                .isNotEmpty();
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

    private static int countSourceLines(JavaClass clazz) throws IOException {
        String pkg = clazz.getPackageName();
        String[] parts = pkg.split("\\.");
        String module = "rag-" + parts[2];
        String relPath = pkg.replace('.', '/') + "/" + clazz.getSimpleName() + ".java";
        Path source = repoRoot().resolve("apps").resolve(module)
                .resolve("src").resolve("test").resolve("java").resolve(relPath);
        return (int) Files.lines(source).count();
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

    private static List<Path> findFile(Path base, Predicate<String> matcher) throws IOException {
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