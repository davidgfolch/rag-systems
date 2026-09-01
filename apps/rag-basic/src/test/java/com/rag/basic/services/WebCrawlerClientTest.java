package com.rag.basic.services;

import com.rag.contract.model.Page;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebCrawlerClientTest {

    @Test
    void fetchesPageAndMapsDto() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8085")
                .requestFactory(new JdkClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebCrawlerClient sut = new WebCrawlerClient(builder.build());
        server.expect(requestTo("http://localhost:8085/api/fetch"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"url":"https://example.com","title":"Example","text":"page text"}
                        """, MediaType.APPLICATION_JSON));

        Page page = sut.fetch("https://example.com");

        assertThat(page.getUrl()).isEqualTo("https://example.com");
        assertThat(page.getTitle()).isEqualTo("Example");
        assertThat(page.getText()).isEqualTo("page text");
        server.verify();
    }
}