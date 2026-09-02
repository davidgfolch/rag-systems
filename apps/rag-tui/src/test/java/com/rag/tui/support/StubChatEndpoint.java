package com.rag.tui.support;

import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Stub WebSocket chat endpoint for {@link WsChatStubApp}: replies to any ask
 * frame with one token event and a done event carrying the final answer.
 */
@Component
@ServerEndpoint("/ws/chat")
public class StubChatEndpoint {

    @OnOpen
    public void onOpen(Session session) {
        // no-op: chat starts when the client sends the ask frame
    }

    @OnMessage
    public void onMessage(Session session, String message) throws IOException {
        session.getBasicRemote().sendText("{\"type\":\"token\",\"content\":\"Hello \"}");
        session.getBasicRemote().sendText("{\"type\":\"done\",\"content\":\"Hello World\"}");
    }
}