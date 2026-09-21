package com.rag.common.ingestion.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Architecture rules for rag-common-ingestion: it may depend on the core kernel
 * but never on the retrieval or generation capabilities, and must stay free of
 * Spring AI and Reactor. Structured logging is enforced per module (the core
 * architecture test only sees the core classpath).
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.common.ingestion";
    private static final String MODULE = "rag-common-ingestion";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void ingestionDoesNotDependOnRetrievalOrGeneration() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rag.common.retrieval..", "com.rag.common.generation..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void ingestionStaysFreeOfSpringAiAndReactor() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.ai..", "reactor..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void everyBusinessClassHasLogger() {
        classes()
                .that().resideInAPackage("..ingestion..")
                .and().areNotInterfaces()
                .and().areNotNestedClasses()
                .and().areNotRecords()
                .should(declareSlf4jLogger())
                .check(importer.importPackages(ROOT));
    }

    @Test
    void testFixturesShouldStartWithTestPrefix() {
        var allClasses = new ClassFileImporter().importPackages(ROOT);
        noClasses()
                .that().resideInAPackage("..testfixture..")
                .should().haveSimpleNameNotStartingWith("Test")
                .check(allClasses);
    }

    @Test
    void testFixturesShouldBeUnder100Lines() {
        var allClasses = new ClassFileImporter().importPackages(ROOT);
        List<String> tooLong = allClasses.stream()
                .filter(c -> c.getPackageName().contains(".testfixture"))
                .map(ArchitectureTest::describeIfTooLong)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        assertThat(tooLong)
                .as("test fixtures should stay under 100 lines to remain focused")
                .isEmpty();
    }

    private static ArchCondition<JavaClass> declareSlf4jLogger() {
        return new ArchCondition<>("declare an SLF4J logger field named 'log'") {
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
        };
    }

    private static String describeIfTooLong(JavaClass c) {
        try {
            int lines = sourceLines(c);
            return lines > 100 ? c.getSimpleName() + " (" + lines + " lines > 100)" : null;
        } catch (IOException _) {
            return c.getSimpleName() + " (cannot read source)";
        }
    }

    private static int sourceLines(JavaClass clazz) throws IOException {
        String rel = clazz.getPackageName().replace('.', '/') + "/" + clazz.getSimpleName() + ".java";
        Path source = repoRoot().resolve("apps").resolve(MODULE)
                .resolve("src").resolve("test").resolve("java").resolve(rel);
        return (int) Files.lines(source).count();
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("scripts"))) {
            dir = dir.getParent();
        }
        return dir;
    }
}
