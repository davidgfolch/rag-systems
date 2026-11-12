package com.rag.common.generation.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.services.ChatModelPort;
import com.rag.common.core.tracing.TracePropagation;
import com.rag.common.generation.StreamingChatModelPort;
import com.rag.contract.provider.CompleteRequest;
import com.rag.contract.provider.CompleteResponse;
import com.rag.contract.ws.ChatResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static com.rag.contract.constants.ApiPaths.CHAT_STREAM;
import static com.rag.contract.constants.ApiPaths.COMPLETE;
import static com.rag.contract.constants.FrameTypes.DONE;
import static com.rag.contract.constants.FrameTypes.ERROR;
import static com.rag.contract.constants.FrameTypes.TOKEN;

/**
 * Adapter bridging the domain {@link ChatModelPort} and
 * {@link StreamingChatModelPort} onto the rag-provider service over HTTP.
 * Streaming reuses the {@link ChatResponse} frame shape, consumed as
 * Server-Sent Events.
 */
public class RemoteChatModelPort implements ChatModelPort, StreamingChatModelPort {

    private static final Logger log = LoggerFactory.getLogger(RemoteChatModelPort.class);

    static final int STREAM_BUFFER_CAPACITY = 256;
    private static final String DATA_PREFIX = "data:";
    private static final String STREAM_FAILED = "Provider stream failed: ";

    private final ProviderHttpClient httpClient;
    private final ObjectMapper om;
    private final Tracer tracer;

    public RemoteChatModelPort(ProviderHttpClient httpClient, ObjectMapper om) {
        this(httpClient, om, null);
    }

    public RemoteChatModelPort(ProviderHttpClient httpClient, ObjectMapper om, Tracer tracer) {
        this.httpClient = httpClient;
        this.om = om;
        this.tracer = tracer;
    }

    @Override
    public String complete(String prompt) {
        var res = httpClient.postJson(COMPLETE, new CompleteRequest(prompt), CompleteResponse.class);
        log.info("Remote chat completion received: promptLength={}, answerLength={}",
                prompt.length(), res.answer() == null ? 0 : res.answer().length());
        return res.answer();
    }

    @Override
    public Flux<String> completeStream(String prompt) {
        log.info("Remote chat stream started: promptLength={}", prompt.length());
        Span span = tracer != null ? tracer.currentSpan() : null;
        Sinks.Many<String> sink = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(STREAM_BUFFER_CAPACITY));
        var bodyRef = new AtomicReference<InputStream>();
        Thread.ofVirtual().start(() -> TracePropagation.runWithSpan(
                tracer, span, () -> readStream(prompt, sink, bodyRef)));
        return sink.asFlux().doOnCancel(() -> closeQuietly(bodyRef.get()));
    }

    private void readStream(String prompt, Sinks.Many<String> sink, AtomicReference<InputStream> bodyRef) {
        try (var body = httpClient.postStream(CHAT_STREAM, new CompleteRequest(prompt));
             var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            bodyRef.set(body);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(DATA_PREFIX)) {
                    var json = line.substring(DATA_PREFIX.length()).trim();
                    if (!json.isEmpty() && !consumeFrame(sink, json)) {
                        return;
                    }
                }
            }
            sink.tryEmitComplete();
        } catch (Exception e) {
            var error = new IllegalStateException(STREAM_FAILED + e.getMessage(), e);
            if (sink.tryEmitError(error) != Sinks.EmitResult.FAIL_CANCELLED) {
                log.error("Remote chat stream failed", e);
            }
        }
    }

    private boolean consumeFrame(Sinks.Many<String> sink, String json) throws IOException {
        var frame = om.readValue(json, ChatResponse.class);
        return switch (frame.type()) {
            case TOKEN -> {
                if (frame.content() != null && sink.tryEmitNext(frame.content()).isFailure()) {
                    log.debug("Consumer stopped accepting tokens; closing provider stream");
                    yield false;
                }
                yield true;
            }
            case ERROR -> {
                sink.tryEmitError(new IllegalStateException(frame.content()));
                yield false;
            }
            case DONE -> {
                sink.tryEmitComplete();
                yield false;
            }
            default -> true;
        };
    }

    private static void closeQuietly(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException e) {
            log.debug("Could not close provider stream: {}", e.getMessage());
        }
    }
}
