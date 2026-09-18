package com.rag.common.tracing;

import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static com.rag.common.tracing.TracePropagation.MDC_SPAN_ID;
import static com.rag.common.tracing.TracePropagation.MDC_TRACE_ID;
import static com.rag.common.tracing.TracePropagation.runWithSpan;
import static com.rag.common.tracing.TracePropagation.w3cTraceparent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TracePropagationTest {

    private final SimpleTracer tracer = new SimpleTracer();

    @BeforeEach
    void clearMdcBefore() {
        MDC.clear();
    }

    @AfterEach
    void clearMdcAfter() {
        MDC.clear();
    }

    @Test
    void shouldBuildW3cTraceparentWhenSpanSampled() {
        var span = tracer.nextSpan().name("op").start();
        span.context().setSampled(true);
        var header = w3cTraceparent(span.context());
        assertThat(header).isEqualTo("00-" + span.context().traceId() + "-" + span.context().spanId() + "-01");
    }

    @Test
    void shouldBuildW3cTraceparentWhenSpanUnsampled() {
        var span = tracer.nextSpan().name("op").start();
        span.context().setSampled(false);
        var header = w3cTraceparent(span.context());
        assertThat(header).isEqualTo("00-" + span.context().traceId() + "-" + span.context().spanId() + "-00");
    }

    @Test
    void shouldBuildW3cTraceparentWhenSamplingUnknown() {
        var span = tracer.nextSpan().name("op").start();
        span.context().setSampled(null);
        var header = w3cTraceparent(span.context());
        assertThat(header).endsWith("-00");
    }

    @Test
    void shouldPutSpanContextIntoMdcWhenRunnableRuns() {
        var span = tracer.nextSpan().name("op").start();
        var traceIdInside = new AtomicReference<String>();
        var spanIdInside = new AtomicReference<String>();
        runWithSpan(tracer, span, () -> {
            traceIdInside.set(MDC.get(MDC_TRACE_ID));
            spanIdInside.set(MDC.get(MDC_SPAN_ID));
        });
        assertThat(traceIdInside.get()).isEqualTo(span.context().traceId());
        assertThat(spanIdInside.get()).isEqualTo(span.context().spanId());
        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(MDC_SPAN_ID)).isNull();
    }

    @Test
    void shouldRestorePreviousMdcValuesAfterRunnable() {
        var span = tracer.nextSpan().name("op").start();
        MDC.put(MDC_TRACE_ID, "previous-trace");
        MDC.put(MDC_SPAN_ID, "previous-span");
        runWithSpan(tracer, span, () -> { });
        assertThat(MDC.get(MDC_TRACE_ID)).isEqualTo("previous-trace");
        assertThat(MDC.get(MDC_SPAN_ID)).isEqualTo("previous-span");
    }

    @Test
    void shouldRemoveMdcKeysWhenNoPreviousValues() {
        var span = tracer.nextSpan().name("op").start();
        runWithSpan(tracer, span, () -> { });
        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(MDC_SPAN_ID)).isNull();
    }

    @Test
    void shouldRunRunnableWhenTracerMissing() {
        var span = tracer.nextSpan().name("op").start();
        var ran = new AtomicReference<String>("no");
        runWithSpan(null, span, () -> ran.set("yes"));
        assertThat(ran.get()).isEqualTo("yes");
        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
    }

    @Test
    void shouldRunRunnableWhenSpanMissing() {
        var ran = new AtomicReference<String>("no");
        runWithSpan(tracer, null, () -> ran.set("yes"));
        assertThat(ran.get()).isEqualTo("yes");
        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
    }

    @Test
    void shouldReturnSupplierResultWithMdc() {
        var span = tracer.nextSpan().name("op").start();
        var traceIdInside = new AtomicReference<String>();
        var result = runWithSpan(tracer, span, () -> {
            traceIdInside.set(MDC.get(MDC_TRACE_ID));
            return "computed";
        });
        assertThat(result).isEqualTo("computed");
        assertThat(traceIdInside.get()).isEqualTo(span.context().traceId());
        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
    }

    @Test
    void shouldReturnSupplierResultWhenTracerMissing() {
        var result = runWithSpan(null, null, () -> "fallback");
        assertThat(result).isEqualTo("fallback");
        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
    }

    @Test
    void shouldReturnSupplierResultWhenSpanMissing() {
        var result = runWithSpan(tracer, null, () -> "fallback");
        assertThat(result).isEqualTo("fallback");
        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
    }

    @Test
    void shouldRestoreMdcWhenRunnableThrows() {
        var span = tracer.nextSpan().name("op").start();
        MDC.put(MDC_TRACE_ID, "previous-trace");
        assertThatThrownBy(() -> runWithSpan(tracer, span, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThat(MDC.get(MDC_TRACE_ID)).isEqualTo("previous-trace");
        assertThat(MDC.get(MDC_SPAN_ID)).isNull();
    }
}
