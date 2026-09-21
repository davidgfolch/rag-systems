package com.rag.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Cross-thread and cross-process trace propagation helpers shared by all RAG
 * modules. Micrometer writes {@code traceId}/{@code spanId} into the MDC, so
 * the structured loggers (logstash JSON and Loki) pick up the same key names
 * that Tempo uses for the W3C trace id ({@link #w3cTraceparent}).
 */
public final class TracePropagation {

    private static final Logger log = LoggerFactory.getLogger(TracePropagation.class);

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final String W3C_VERSION = "00";
    private static final String FLAGS_SAMPLED = "01";
    private static final String FLAGS_UNSAMPLED = "00";
    private static final Pattern W3C_TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern W3C_SPAN_ID = Pattern.compile("[0-9a-f]{16}");

    private TracePropagation() {
    }

    public static String w3cTraceparent(TraceContext context) {
        String traceId = context.traceId();
        String spanId = context.spanId();
        if (!W3C_TRACE_ID.matcher(traceId == null ? "" : traceId).matches()
                || !W3C_SPAN_ID.matcher(spanId == null ? "" : spanId).matches()) {
            log.warn("Skipping traceparent propagation: tracer produced non-conformant W3C ids "
                    + "(traceId length={}, spanId length={})",
                    traceId == null ? 0 : traceId.length(), spanId == null ? 0 : spanId.length());
            return null;
        }
        String flags = Boolean.TRUE.equals(context.sampled()) ? FLAGS_SAMPLED : FLAGS_UNSAMPLED;
        return W3C_VERSION + "-" + traceId + "-" + spanId + "-" + flags;
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