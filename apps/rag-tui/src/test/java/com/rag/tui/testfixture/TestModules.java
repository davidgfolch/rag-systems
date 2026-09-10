package com.rag.tui.testfixture;

import com.rag.tui.launcher.Module;

import java.util.List;

public final class TestModules {

    public static final String BASIC = "rag-basic";
    public static final String ADVANCED = "rag-advanced";
    public static final String AGENTIC = "rag-agentic";
    public static final String PROVIDER = "rag-provider";
    public static final String BASIC_URL = "http://localhost:8081";
    public static final String ADVANCED_URL = "http://localhost:8082";
    public static final String AGENTIC_URL = "http://localhost:8083";
    public static final String PROVIDER_URL = "http://localhost:8086";

    private TestModules() {
    }

    public static Module basic() {
        return new Module(BASIC, BASIC_URL);
    }

    public static Module advanced() {
        return new Module(ADVANCED, ADVANCED_URL);
    }

    public static Module agentic() {
        return new Module(AGENTIC, AGENTIC_URL);
    }

    public static Module provider() {
        return new Module(PROVIDER, PROVIDER_URL);
    }

    public static Module withUrl(String url) {
        return new Module(BASIC, url);
    }

    public static Module named(String name, String url) {
        return new Module(name, url);
    }

    public static List<Module> all() {
        return List.of(basic(), advanced(), agentic(), provider());
    }
}