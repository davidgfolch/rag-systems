package com.rag.basic.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules for rag-basic, enforced by ArchUnit.
 * All chunking/parsing/embedding/vector-store strategy implementations live in
 * rag-common; rag-basic only owns its API layer and retrieval service.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.basic";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void controllersDependOnlyOnServices() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("api").definedBy("..api..")
                .layer("services").definedBy("..services..")
                .whereLayer("api").mayOnlyAccessLayers("services")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void controllersDoNotDependOnConfigOrCommonRepositories() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..config..", "com.rag.common.repositories")
                .check(importer.importPackages(ROOT));
    }
}