package com.rag.tui.architecture;

import com.rag.common.repositories.VectorStore;
import com.rag.common.services.ChatModel;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModel;
import com.rag.common.services.TextSplitter;
import com.rag.tui.fetching.WebPageFetcher;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules for rag-tui, enforced by ArchUnit.
 * The ui layer bridges services to the terminal; services and fetching depend
 * only on the rag-common strategy interfaces, never on concrete providers.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.tui";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void uiDoesNotDependOnInternals() {
        noClasses()
                .that().resideInAPackage("..ui..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..config..", "..adapter..", "..chunking..", "..parsing..",
                        "..vectorstore..", "com.rag.common.repositories")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void servicesDoNotDependOnProvidersOrConfig() {
        noClasses()
                .that().resideInAPackage("..services..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..config..", "..adapter..", "..chunking..", "..parsing..", "..vectorstore..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void fetchersImplementWebPageFetcher() {
        classes()
                .that().resideInAPackage("..fetching..")
                .and().haveSimpleNameEndingWith("WebPageFetcher")
                .and().areNotInterfaces()
                .and().areNotNestedClasses()
                .should().implement(WebPageFetcher.class)
                .check(importer.importPackages(ROOT));
    }

    @Test
    void chunkersImplementTextSplitter() {
        classes()
                .that().resideInAPackage("..chunking..")
                .should().implement(TextSplitter.class)
                .check(importer.importPackages(ROOT));
    }

    @Test
    void parsersImplementDocumentParser() {
        classes()
                .that().resideInAPackage("..parsing..")
                .should().implement(DocumentParser.class)
                .check(importer.importPackages(ROOT));
    }

    @Test
    void adaptersImplementTheirStrategyInterface() {
        classes()
                .that().resideInAPackage("..adapter..").and().haveSimpleNameEndingWith("ChatModel")
                .should().implement(ChatModel.class)
                .orShould().implement(EmbeddingModel.class)
                .check(importer.importPackages(ROOT));
    }

    @Test
    void vectorStoresImplementVectorStore() {
        classes()
                .that().resideInAPackage("..vectorstore..").and().areNotNestedClasses()
                .should().implement(VectorStore.class)
                .check(importer.importPackages(ROOT));
    }
}
