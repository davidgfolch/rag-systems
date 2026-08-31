package com.rag.common.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for rag-common.
 * Domain contains only pure, dependency-free models; services and repositories
 * may depend on domain but not on each other.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.common";

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
                .whereLayer("services").mayOnlyAccessLayers("domain")
                .whereLayer("repositories").mayOnlyAccessLayers("domain")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void domainDependsOnNothing() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..services..", "..repositories..")
                .check(importer.importPackages(ROOT));
    }
}