package com.rag.memory.api;

import com.rag.contract.model.ChatMessageDTO;
import com.rag.contract.model.ConversationDTO;
import com.rag.memory.services.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ConversationDTO> listConversations() {
        List<ConversationDTO> conversations = conversationService.listConversations();
        log.info("List conversations -> {} results", conversations.size());
        return conversations;
    }

    @PostMapping
    public ResponseEntity<ConversationDTO> createConversation(
            @RequestParam(required = false) String title) {
        ConversationDTO conversation = conversationService.createConversation(title);
        log.info("Created conversation {}", conversation.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    @GetMapping("/{conversationId}/messages")
    public List<ChatMessageDTO> listMessages(@PathVariable String conversationId) {
        List<ChatMessageDTO> messages = conversationService.listMessages(conversationId);
        log.info("List {} messages for conversation {}", messages.size(), conversationId);
        return messages;
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ChatMessageDTO> addMessage(@PathVariable String conversationId,
                                                     @RequestBody ChatMessageDTO message) {
        ChatMessageDTO saved = conversationService.addMessage(conversationId, message);
        log.info("Saved message {} to conversation {}", saved.getId(), conversationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}