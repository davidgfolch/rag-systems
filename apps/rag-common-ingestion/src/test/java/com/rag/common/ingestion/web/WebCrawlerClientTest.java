package com.rag.common.ingestion.web;

import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebCrawlerClientTest {

    private static final String BASE_URL = "http://localhost:8085";

    @Test
    void fetchesPageAndMapsDto() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build());
        server.expect(requestTo(BASE_URL + "/api/fetch"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"url":"https://example.com","title":"Example","text":"page text"}
                        """, APPLICATION_JSON));
        PageDTO page = sut.fetch("https://example.com");
        assertThat(page.getUrl()).isEqualTo("https://example.com");
        assertThat(page.getTitle()).isEqualTo("Example");
        assertThat(page.getText()).isEqualTo("page text");
        server.verify();
    }

    @Test
    void ranksLinksViaWebcrawler() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build());
        server.expect(requestTo(BASE_URL + "/api/rank-links"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"links":["https://example.com/b","https://example.com/a"]}
                        """, APPLICATION_JSON));
        List<String> ranked = sut.rankLinks(
                List.of("https://example.com/a", "https://example.com/b"), "question");
        assertThat(ranked).containsExactly("https://example.com/b", "https://example.com/a");
        server.verify();
    }

    @Test
    void rankLinksSkipsHttpCallWhenThereAreNoLinks() {
        var sut = new WebCrawlerClient(null);
        assertThat(sut.rankLinks(List.of(), "question")).isEmpty();
        assertThat(sut.rankLinks(null, "question")).isEmpty();
    }

    @Test
    void discoversUrlsViaWebcrawler() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build());
        server.expect(requestTo(BASE_URL + "/api/discover"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"urls":["https://example.com/docs/a","https://example.com/docs/b"]}
                        """, APPLICATION_JSON));
        List<String> urls = sut.discover("https://example.com/docs/");
        assertThat(urls).containsExactly("https://example.com/docs/a", "https://example.com/docs/b");
        server.verify();
    }

    private static RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(new JdkClientHttpRequestFactory());
    }
}
