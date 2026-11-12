package com.rag.common.core.architecture;

import com.rag.common.core.testfixture.TestRepoPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-module gate: production code must serve production. A public,
 * protected or package-private member declared under {@code src/main} must be
 * referenced by production sources; a member referenced only by tests belongs
 * in test scope (private test helper or a {@code Test*} fixture), and a member
 * referenced nowhere is dead. See {@code docs/process/asdlc.md}.
 *
 * <p>Runtime entry points (framework callbacks, bean methods, controllers,
 * entity/DTO accessors) are excluded by {@link MemberUsageScanner}; genuinely
 * retained exceptions go into the documented allowlist below.
 */
class TestOnlyMembersArchitectureTest {

    private static final Set<String> ALLOWLIST = Set.of(
            // Injectable input/rendering seam: the production ctor builds two
            // terminal sources; tests drive the real prompt loop with scripted
            // keys and an in-memory sink, which no public ctor can express.
            "InteractivePrompter#InteractivePrompter/4",
            // HTTP-client seams: deterministic transport tests without a live
            // socket; production wiring uses the shorter ctor, which delegates.
            "ProviderHttpClient#ProviderHttpClient/6",
            "JsoupWebPageFetcher#JsoupWebPageFetcher/4",
            "HttpUrlContentLoader#HttpUrlContentLoader/3",
            // Process-starter seam: tests supply a fake child process instead of
            // launching scripts/run.sh; production uses the default starter.
            "ModuleLifecycleManager#ModuleLifecycleManager/2",
            // Poll-interval seam: integration tests need a fast poll; production
            // uses the real poller through the public ctor.
            "IngestCommands#IngestCommands/5",
            // Breakpoint-override seam: production always uses the default
            // percentile; the override exists to test the validation bounds.
            "SemanticChunker#SemanticChunker/4");

    @Test
    void productionMembersAreReferencedByProductionCode() throws IOException {
        List<String> violations = MemberUsageScanner.violations(TestRepoPaths.root(), ALLOWLIST);
        if (!violations.isEmpty()) {
            System.out.println("Test-only / orphaned production members (" + violations.size() + "):");
            violations.forEach(v -> System.out.println("  " + v));
        }
        assertThat(violations)
                .as("production members must be referenced by production code; move test convenience "
                        + "into a private test helper or a Test* fixture (docs/process/asdlc.md)")
                .isEmpty();
    }
}
