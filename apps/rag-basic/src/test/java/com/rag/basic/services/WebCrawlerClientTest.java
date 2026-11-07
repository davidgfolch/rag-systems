package com.rag.basic.services;

import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

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
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"url":"https://example.com","title":"Example","text":"page text"}
                        """, MediaType.APPLICATION_JSON));
        PageDTO page = sut.fetch("https://example.com");
        assertThat(page.getUrl()).isEqualTo("https://example.com");
        assertThat(page.getTitle()).isEqualTo("Example");
        assertThat(page.getText()).isEqualTo("page text");
        server.verify();
    }

    @Test
    void ranksLinksViaWebcrawler() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8085")
                .requestFactory(new JdkClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebCrawlerClient sut = new WebCrawlerClient(builder.build());
        server.expect(requestTo("http://localhost:8085/api/rank-links"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"links":["https://example.com/b","https://example.com/a"]}
                        """, MediaType.APPLICATION_JSON));
        List<String> ranked = sut.rankLinks(
                List.of("https://example.com/a", "https://example.com/b"), "question");
        assertThat(ranked).containsExactly("https://example.com/b", "https://example.com/a");
        server.verify();
    }

    @Test
    void rankLinksSkipsHttpCallWhenThereAreNoLinks() {
        WebCrawlerClient sut = new WebCrawlerClient(null);
        assertThat(sut.rankLinks(List.of(), "question")).isEmpty();
        assertThat(sut.rankLinks(null, "question")).isEmpty();
    }

    @Test
    void discoversUrlsViaWebcrawler() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8085")
                .requestFactory(new JdkClientHttpRequestFactory());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebCrawlerClient sut = new WebCrawlerClient(builder.build());
        server.expect(requestTo("http://localhost:8085/api/discover"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"urls":["https://example.com/docs/a","https://example.com/docs/b"]}
                        """, MediaType.APPLICATION_JSON));
        List<String> urls = sut.discover("https://example.com/docs/");
        assertThat(urls).containsExactly("https://example.com/docs/a", "https://example.com/docs/b");
        server.verify();
    }
}