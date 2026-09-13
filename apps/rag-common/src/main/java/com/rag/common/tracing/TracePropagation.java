package com.rag.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

import java.util.function.Supplier;

/**
 * Cross-thread and cross-process trace propagation helpers shared by all RAG
 * modules. Micrometer writes {@code traceId}/{@code spanId} into the MDC, so
 * the structured loggers (logstash JSON and Loki) pick up the same key names
 * that Tempo uses for the W3C trace id ({@link #w3cTraceparent}).
 */
public final class TracePropagation {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final String W3C_VERSION = "00";
    private static final String FLAGS_SAMPLED = "01";
    private static final String FLAGS_UNSAMPLED = "00";

    private TracePropagation() {
    }

    public static String w3cTraceparent(TraceContext context) {
        String flags = Boolean.TRUE.equals(context.sampled()) ? FLAGS_SAMPLED : FLAGS_UNSAMPLED;
        return W3C_VERSION + "-" + context.traceId() + "-" + context.spanId() + "-" + flags;
    }

    public static void runWithSpan(Tracer tracer, Span span, Runnable task) {
        if (tracer == null || span == null) {
            task.run();
            return;
        }
        var previousTrace = MDC.get(MDC_TRACE_ID);
        var previousSpan = MDC.get(MDC_SPAN_ID);
        MDC.put(MDC_TRACE_ID, span.context().traceId());
        MDC.put(MDC_SPAN_ID, span.context().spanId());
        try (var _ = tracer.withSpan(span)) {
            task.run();
        } finally {
            restore(MDC_TRACE_ID, previousTrace);
            restore(MDC_SPAN_ID, previousSpan);
        }
    }

    public static <T> T runWithSpan(Tracer tracer, Span span, Supplier<T> task) {
        if (tracer == null || span == null) {
            return task.get();
        }
        var previousTrace = MDC.get(MDC_TRACE_ID);
        var previousSpan = MDC.get(MDC_SPAN_ID);
        MDC.put(MDC_TRACE_ID, span.context().traceId());
        MDC.put(MDC_SPAN_ID, span.context().spanId());
        try (var _ = tracer.withSpan(span)) {
            return task.get();
        } finally {
            restore(MDC_TRACE_ID, previousTrace);
            restore(MDC_SPAN_ID, previousSpan);
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }
}