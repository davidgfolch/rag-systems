package com.rag.basic.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules for rag-basic, enforced by ArchUnit.
 * Controllers depend only on services; services depend only on the rag-common
 * strategy interfaces (never on concrete chunkers/parsers/embedders/stores).
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
    void servicesDoNotDependOnConcreteStrategies() {
        noClasses()
                .that().resideInAPackage("..services..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..chunking..", "..parsing..", "..embedding..", "..vectorstore..", "..config..")
                .because("services should depend only on the rag-common strategy interfaces")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void controllersDoNotDependOnInternals() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..vectorstore..", "..chunking..", "..parsing..", "..embedding..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void chunkersImplementTextSplitter() {
        classes()
                .that().resideInAPackage("..chunking..")
                .should().implement(com.rag.common.services.TextSplitter.class)
                .check(importer.importPackages(ROOT));
    }

    @Test
    void parsersImplementDocumentParser() {
        classes()
                .that().resideInAPackage("..parsing..")
                .should().implement(com.rag.common.services.DocumentParser.class)
                .check(importer.importPackages(ROOT));
    }

    @Test
    void embedderImplementsEmbeddingModel() {
        classes()
                .that().resideInAPackage("..embedding..")
                .should().implement(com.rag.common.services.EmbeddingModel.class)
                .check(importer.importPackages(ROOT));
    }

    @Test
    void vectorStoresImplementVectorStoreInterface() {
        classes()
                .that().resideInAPackage("..vectorstore..").and().areNotNestedClasses()
                .should().implement(com.rag.common.repositories.VectorStore.class)
                .check(importer.importPackages(ROOT));
    }
}