package com.rag.common.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.services.ChatModelPort;
import com.rag.common.tracing.TracePropagation;
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.rag.contract.constants.ApiPaths.CHAT_STREAM;
import static com.rag.contract.constants.ApiPaths.COMPLETE;
import static com.rag.contract.constants.FrameTypes.DONE;
import static com.rag.contract.constants.FrameTypes.ERROR;
import static com.rag.contract.constants.FrameTypes.TOKEN;

/**
 * Adapter bridging the domain {@link ChatModelPort} onto the rag-provider
 * service over HTTP. Streaming reuses the {@link ChatResponse} frame shape,
 * consumed as Server-Sent Events.
 */
public class RemoteChatModelPort implements ChatModelPort {

    private static final Logger log = LoggerFactory.getLogger(RemoteChatModelPort.class);

    private final ProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public RemoteChatModelPort(ProviderHttpClient httpClient, ObjectMapper objectMapper) {
        this(httpClient, objectMapper, null);
    }

    public RemoteChatModelPort(ProviderHttpClient httpClient, ObjectMapper objectMapper, Tracer tracer) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    public String complete(String prompt) {
        var response = httpClient.postJson(COMPLETE, new CompleteRequest(prompt), CompleteResponse.class);
        log.info("Remote chat completion received: promptLength={}, answerLength={}",
                prompt.length(), response.answer() == null ? 0 : response.answer().length());
        return response.answer();
    }

    @Override
    public Flux<String> completeStream(String prompt) {
        log.info("Remote chat stream started: promptLength={}", prompt.length());
        Span span = tracer != null ? tracer.currentSpan() : null;
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread.ofVirtual().start(() -> TracePropagation.runWithSpan(tracer, span, () -> readStream(prompt, sink)));
        return sink.asFlux();
    }

    private void readStream(String prompt, Sinks.Many<String> sink) {
        try (var body = httpClient.postStream(CHAT_STREAM, new CompleteRequest(prompt));
             var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    var json = line.substring("data:".length()).trim();
                    if (!json.isEmpty() && !consumeFrame(sink, json)) {
                        return;
                    }
                }
            }
            sink.tryEmitComplete();
        } catch (Exception e) {
            log.error("Remote chat stream failed", e);
            sink.tryEmitError(new IllegalStateException("Provider stream failed: " + e.getMessage(), e));
        }
    }

    private boolean consumeFrame(Sinks.Many<String> sink, String json) throws IOException {
        var frame = objectMapper.readValue(json, ChatResponse.class);
        return switch (frame.type()) {
            case TOKEN -> {
                if (frame.content() != null) {
                    sink.tryEmitNext(frame.content());
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
}