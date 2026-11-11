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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.rag.contract.constants.ApiPaths.CHAT_STREAM;
import static com.rag.contract.constants.ApiPaths.COMPLETE;
import static com.rag.contract.constants.ApiPaths.EMBED;
import static com.rag.contract.constants.FrameTypes.DONE;
import static com.rag.contract.constants.FrameTypes.ERROR;
import static com.rag.contract.constants.FrameTypes.TOKEN;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

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

    @PostMapping(COMPLETE)
    public CompleteResponse complete(@RequestBody CompleteRequest req) {
        var answer = chatService.complete(req.prompt());
        log.info("Completion served: promptLength={}, answerLength={}", req.prompt().length(), answer.length());
        return new CompleteResponse(answer);
    }

    @PostMapping(EMBED)
    public EmbedResponse embed(@RequestBody EmbedRequest req) {
        var vectors = embeddingService.embed(req.texts(), req.model());
        log.info("Embedding served: texts={}, dimension={}",
                vectors.size(), vectors.isEmpty() ? 0 : vectors.getFirst().size());
        return new EmbedResponse(vectors);
    }

    @PostMapping(value = CHAT_STREAM, produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody CompleteRequest req) {
        var emitter = new SseEmitter(0L);
        log.info("Chat stream started: promptLength={}", req.prompt().length());
        var subscription = chatService.stream(req.prompt()).subscribe(
                token -> send(emitter, new ChatResponse(TOKEN, token, null)),
                error -> fail(emitter, error),
                () -> {
                    send(emitter, new ChatResponse(DONE, null, null));
                    emitter.complete();
                    log.info("Chat stream completed: promptLength={}", req.prompt().length());
                });
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            log.warn("Chat stream emitter timed out, disposing subscription");
            subscription.dispose();
            emitter.complete();
        });
        emitter.onError(_ -> subscription.dispose());
        return emitter;
    }

    private void fail(SseEmitter emitter, Throwable error) {
        log.error("Chat stream failed", error);
        send(emitter, new ChatResponse(ERROR, error.getMessage(), null));
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