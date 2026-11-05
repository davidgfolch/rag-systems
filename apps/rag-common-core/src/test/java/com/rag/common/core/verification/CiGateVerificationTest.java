package com.rag.common.core.verification;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/**
 * Temporary probe used to verify that a code-changing pull request runs the full CI matrix and
 * that the required "CI gate" check blocks the merge when a test fails. Deleted immediately after
 * the verification run; never merged.
 */
class CiGateVerificationTest {

    @Test
    void shouldFailSoTheCiGateBlocksTheMerge() {
        fail("Intentional failure: verifying that CI gate blocks a red merge");
    }
}
