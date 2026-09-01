package com.rag.tui.client;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.ChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MemoryClientTest {

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://localhost:8084")
            .requestFactory(new JdkClientHttpRequestFactory());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MemoryClient sut = new MemoryClient(builder.build());

    @Test
    void listsConversations() {
        server.expect(requestTo("http://localhost:8084/api/conversations"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"c1\",\"title\":\"t1\"}]",
                        MediaType.APPLICATION_JSON));

        List<ConversationDTO> conversations = sut.conversations();

        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).getId()).isEqualTo("c1");
        server.verify();
    }

    @Test
    void returnsEmptyWhenNoConversations() {
        server.expect(requestTo("http://localhost:8084/api/conversations"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThat(sut.conversations()).isEmpty();
        server.verify();
    }

    @Test
    void listsMessagesForConversation() {
        server.expect(requestTo("http://localhost:8084/api/conversations/c1/messages"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"id\":\"m1\",\"content\":\"hi\",\"role\":\"user\",\"conversationId\":\"c1\"}]",
                        MediaType.APPLICATION_JSON));

        List<ChatMessageDTO> messages = sut.messages("c1");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("hi");
        server.verify();
    }
}