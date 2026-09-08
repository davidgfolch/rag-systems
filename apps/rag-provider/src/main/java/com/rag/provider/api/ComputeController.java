package com.rag.provider.api;

import com.rag.contract.provider.CompleteRequest;
import com.rag.contract.provider.CompleteResponse;
import com.rag.contract.provider.EmbedRequest;
import com.rag.contract.provider.EmbedResponse;
import com.rag.contract.ws.ChatResponse;
import com.rag.provider.services.ChatService;
import com.rag.provider.services.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Model-level compute endpoints consumed by the RAG modules' remote bridges.
 * Streaming reuses the {@link ChatResponse} stream frame shape ("token"/"done"/
 * "error") so remote clients parse one frame format.
 */
@RestController
public class ComputeController {

    private static final Logger log = LoggerFactory.getLogger(ComputeController.class);

    private final ChatService chatService;
    private final EmbeddingService embeddingService;

    public ComputeController(ChatService chatService, EmbeddingService embeddingService) {
        this.chatService = chatService;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/api/complete")
    public CompleteResponse complete(@RequestBody CompleteRequest request) {
        var answer = chatService.complete(request.prompt());
        log.info("Completion served: promptLength={}, answerLength={}", request.prompt().length(), answer.length());
        return new CompleteResponse(answer);
    }

    @PostMapping("/api/embed")
    public EmbedResponse embed(@RequestBody EmbedRequest request) {
        var vectors = embeddingService.embed(request.texts());
        log.info("Embedding served: texts={}, dimension={}",
                vectors.size(), vectors.isEmpty() ? 0 : vectors.get(0).size());
        return new EmbedResponse(vectors);
    }

    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody CompleteRequest request) {
        var emitter = new SseEmitter();
        var subscription = chatService.stream(request.prompt()).subscribe(
                token -> send(emitter, new ChatResponse("token", token, null)),
                error -> fail(emitter, error),
                () -> {
                    send(emitter, new ChatResponse("done", null, null));
                    emitter.complete();
                });
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            subscription.dispose();
            emitter.complete();
        });
        emitter.onError(error -> subscription.dispose());
        return emitter;
    }

    private void fail(SseEmitter emitter, Throwable error) {
        log.error("Chat stream failed", error);
        send(emitter, new ChatResponse("error", error.getMessage(), null));
        emitter.complete();
    }

    private void send(SseEmitter emitter, ChatResponse frame) {
        try {
            emitter.send(frame);
        } catch (Exception e) {
            log.warn("Failed to send stream frame {}: {}", frame.type(), e.getMessage());
        }
    }
}