package com.rag.tui.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules for the thin rag-tui, enforced by ArchUnit. The TUI is a
 * control plane: launcher owns process/registry logic, clients talk to modules
 * over REST/WS, services load files, and ui routes commands. No RAG logic.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.tui";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void layersMayOnlyDependInward() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("ui").definedBy("..ui..")
                .layer("services").definedBy("..services..")
                .layer("launcher").definedBy("..launcher..")
                .layer("client").definedBy("..client..")
                .whereLayer("ui").mayOnlyAccessLayers("services", "launcher", "client")
                .whereLayer("client").mayOnlyAccessLayers("launcher")
                .whereLayer("services").mayOnlyAccessLayers("launcher")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void noDependencyOnCommonRagInternals() {
        noClasses()
                .that().resideInAPackage(ROOT)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rag.common.services", "com.rag.common.repositories",
                        "com.rag.common.domain", "com.rag.common.adapter")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void uiDoesNotDependOnConfig() {
        noClasses()
                .that().resideInAPackage("..ui..")
                .should().dependOnClassesThat().resideInAPackage("..config..")
                .check(importer.importPackages(ROOT));
    }
}