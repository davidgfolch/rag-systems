package com.rag.provider.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules for rag-provider, enforced by ArchUnit. The module is
 * self-contained: domain is pure, adapters build Spring AI clients, services
 * route models, and the API only talks to services. Layer packages are pinned
 * to the module root so Spring AI's own {@code *.api} packages are not matched.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.provider..";
    private static final String DOMAIN = "com.rag.provider.domain..";
    private static final String ADAPTER = "com.rag.provider.adapter..";
    private static final String SERVICES = "com.rag.provider.services..";
    private static final String API = "com.rag.provider.api..";
    private static final String CONFIG = "com.rag.provider.config..";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void layersRespectDependencyFlow() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy(DOMAIN)
                .layer("adapter").definedBy(ADAPTER)
                .layer("services").definedBy(SERVICES)
                .layer("api").definedBy(API)
                .whereLayer("domain").mayNotAccessAnyLayer()
                .whereLayer("adapter").mayOnlyAccessLayers("domain")
                .whereLayer("services").mayOnlyAccessLayers("domain", "adapter")
                .whereLayer("api").mayOnlyAccessLayers("services")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void servicesDoNotDependOnCommonServicesOrRepositories() {
        noClasses()
                .that().resideInAPackage(SERVICES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rag.common.services..", "com.rag.common.repositories..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void apiDoesNotDependOnConfig() {
        noClasses()
                .that().resideInAPackage(API)
                .should().dependOnClassesThat().resideInAnyPackage(CONFIG)
                .check(importer.importPackages(ROOT));
    }
}