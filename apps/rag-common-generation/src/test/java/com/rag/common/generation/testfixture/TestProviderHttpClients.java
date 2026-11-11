package com.rag.common.generation.testfixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.generation.adapter.ProviderHttpClient;
import com.rag.common.generation.adapter.ProviderHttpSettings;
import io.micrometer.tracing.Tracer;

import java.net.http.HttpClient;

/** Builds {@link ProviderHttpClient} instances through the production constructors. */
public final class TestProviderHttpClients {

    private TestProviderHttpClients() {
    }

    public static ProviderHttpClient of(String baseUrl, ObjectMapper mapper) {
        return withTracer(baseUrl, mapper, null);
    }

    public static ProviderHttpClient withTracer(String baseUrl, ObjectMapper mapper, Tracer tracer) {
        return new ProviderHttpClient(baseUrl, mapper, tracer, ProviderHttpSettings.defaults(),
                RetryObserver.NOOP);
    }

    public static ProviderHttpClient withHttpClient(String baseUrl, HttpClient httpClient,
                                                     ObjectMapper mapper) {
        return new ProviderHttpClient(baseUrl, httpClient, mapper, null, ProviderHttpSettings.defaults(),
                RetryObserver.NOOP);
    }
}
