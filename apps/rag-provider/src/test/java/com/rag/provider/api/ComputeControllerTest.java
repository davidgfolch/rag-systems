package com.rag.provider.api;

import com.rag.provider.services.ChatService;
import com.rag.provider.services.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.util.List;

import static com.rag.contract.constants.ApiPaths.CHAT_STREAM;
import static com.rag.contract.constants.ApiPaths.COMPLETE;
import static com.rag.contract.constants.ApiPaths.EMBED;
import static com.rag.contract.constants.FrameTypes.DONE;
import static com.rag.contract.constants.FrameTypes.ERROR;
import static com.rag.contract.constants.FrameTypes.TOKEN;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ComputeControllerTest {

    @Mock private ChatService chatService;
    @Mock private EmbeddingService embeddingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ComputeController(chatService, embeddingService)).build();
    }

    @Test
    void shouldCompleteNonStreaming() throws Exception {
        when(chatService.complete("hi")).thenReturn("Hello!");

        mockMvc.perform(post(COMPLETE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hello!"));
    }

    @Test
    void shouldEmbedTexts() throws Exception {
        when(embeddingService.embed(List.of("a", "b"))).thenReturn(
                List.of(List.of(0.1f), List.of(0.2f)));

        mockMvc.perform(post(EMBED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texts\":[\"a\",\"b\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddings[0][0]").value(0.1))
                .andExpect(jsonPath("$.embeddings[1][0]").value(0.2));
    }

    @Test
    void shouldStreamTokensThenDone() throws Exception {
        when(chatService.stream("hi")).thenReturn(Flux.just("Hel", "lo"));

        var mvcResult = mockMvc.perform(post(CHAT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"" + TOKEN + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"" + DONE + "\"")));
    }

    @Test
    void shouldStreamErrorFrameOnFailure() throws Exception {
        when(chatService.stream("boom")).thenReturn(Flux.error(new IllegalStateException("down")));

        var mvcResult = mockMvc.perform(post(CHAT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"boom\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"" + ERROR + "\"")));
    }
}