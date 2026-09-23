package com.rag.common.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for rag-common-app: it only hosts {@code @Configuration}
 * wiring that pulls strategy beans from the shared capability modules, and it
 * must never depend on a runnable app module or on a provider-specific model.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.common.app";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void configOnlyModuleStaysFreeOfAppAndProviderDependencies() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rag.basic..", "com.rag.advanced..", "com.rag.agentic..",
                        "com.rag.provider..", "com.rag.memory..", "com.rag.webcrawler..",
                        "com.rag.evaluation..", "com.rag.observability..", "com.rag.cli..", "com.rag.tui..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void onlyConfigurationClassesAreAllowed() {
        classes()
                .that().resideInAPackage("..config..")
                .should().haveSimpleNameEndingWith("Configuration")
                .andShould().beAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                .check(importer.importPackages(ROOT));
    }
}