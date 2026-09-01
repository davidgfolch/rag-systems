package com.rag.webcrawler.architecture;

import com.rag.webcrawler.services.fetching.WebPageFetcher;
import com.rag.webcrawler.services.ranking.LinkPrioritizer;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final String ROOT = "com.rag.webcrawler";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void apiDoesNotDependOnInternals() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..config..", "..fetching..", "..ranking..")
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
    void prioritizersImplementLinkPrioritizer() {
        classes()
                .that().resideInAPackage("..ranking..")
                .and().haveSimpleNameEndingWith("LinkPrioritizer")
                .and().areNotInterfaces()
                .and().areNotNestedClasses()
                .should().implement(LinkPrioritizer.class)
                .check(importer.importPackages(ROOT));
    }
}