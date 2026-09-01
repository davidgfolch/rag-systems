package com.rag.memory.api;

import com.rag.contract.model.ChatMessage;
import com.rag.contract.model.Conversation;
import com.rag.memory.services.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<Conversation> listConversations() {
        return conversationService.listConversations();
    }

    @PostMapping
    public ResponseEntity<Conversation> createConversation(
            @RequestParam(required = false) String title) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createConversation(title));
    }

    @GetMapping("/{conversationId}/messages")
    public List<ChatMessage> listMessages(@PathVariable String conversationId) {
        return conversationService.listMessages(conversationId);
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ChatMessage> addMessage(@PathVariable String conversationId,
                                                  @RequestBody ChatMessage message) {
        ChatMessage saved = conversationService.addMessage(conversationId, message);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}