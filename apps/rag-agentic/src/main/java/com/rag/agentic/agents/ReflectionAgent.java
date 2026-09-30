package com.rag.agentic.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Self-reflection: the sufficiency check that decides whether the agent can
 * answer with the context already gathered or must keep retrieving. Answers as
 * soon as one source is available, otherwise it keeps walking the plan until
 * the step budget is spent. Deliberately deterministic so the loop is bounded
 * and testable without extra LLM round-trips.
 */
public class ReflectionAgent {

    private static final Logger log = LoggerFactory.getLogger(ReflectionAgent.class);

    public boolean shouldContinue(boolean hasContext, int stepsUsed, int maxSteps) {
        boolean keepGoing = !hasContext && stepsUsed < maxSteps;
        log.debug("Reflection: hasContext={}, stepsUsed={}, maxSteps={} -> continue={}",
                hasContext, stepsUsed, maxSteps, keepGoing);
        return keepGoing;
    }
}