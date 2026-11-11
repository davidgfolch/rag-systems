package com.rag.common.ingestion.web;

import com.rag.common.core.resilience.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebCrawlerClientRetryTest {

    private static final String BASE_URL = "http://localhost:8085";
    private static final String FETCH_URL = BASE_URL + "/api/fetch";
    private static final int RETRY_ATTEMPTS = 3;
    private static final RetryPolicy FAST_RETRY =
            new RetryPolicy(RETRY_ATTEMPTS, Duration.ofMillis(1), Duration.ofMillis(1), 2.0, 0);

    @Test
    void shouldRetryTransientServerErrorThenSucceed() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build(), FAST_RETRY);
        server.expect(requestTo(FETCH_URL)).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo(FETCH_URL)).andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"url":"https://example.com","title":"Example","text":"page text"}
                        """, APPLICATION_JSON));

        var page = sut.fetch("https://example.com");

        assertThat(page.getTitle()).isEqualTo("Example");
        server.verify();
    }

    @Test
    void shouldGiveUpAfterRetriesAreExhausted() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build(), FAST_RETRY);
        server.expect(requestTo(FETCH_URL)).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo(FETCH_URL)).andRespond(withStatus(SERVICE_UNAVAILABLE));
        server.expect(requestTo(FETCH_URL)).andRespond(withStatus(SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> sut.fetch("https://example.com"))
                .isInstanceOf(RestClientResponseException.class)
                .hasMessageContaining("503");
        server.verify();
    }

    @Test
    void shouldNotRetryClientErrors() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build(), FAST_RETRY);
        server.expect(requestTo(FETCH_URL)).andRespond(withStatus(BAD_REQUEST));

        assertThatThrownBy(() -> sut.fetch("https://example.com"))
                .isInstanceOf(RestClientResponseException.class)
                .hasMessageContaining("400");
        server.verify();
    }

    @Test
    void shouldRetryDiscoveryCalls() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build(), FAST_RETRY);
        server.expect(requestTo(BASE_URL + "/api/discover")).andRespond(withStatus(TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE_URL + "/api/discover"))
                .andRespond(withSuccess("{\"urls\":[\"https://example.com/a\"]}", APPLICATION_JSON));

        assertThat(sut.discover("https://example.com")).containsExactly("https://example.com/a");
        server.verify();
    }

    @Test
    void shouldRetryTimeoutsButNotLogicErrors() {
        assertThat(WebCrawlerClient.isRetryable(
                new ResourceAccessException("timeout", new ConnectException("refused")))).isTrue();
        assertThat(WebCrawlerClient.isRetryable(new IllegalArgumentException("bad body"))).isFalse();
    }

    @Test
    void legacyConstructorShouldNotRetry() {
        var builder = restClientBuilder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var sut = new WebCrawlerClient(builder.build());
        server.expect(requestTo(FETCH_URL)).andRespond(withStatus(SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> sut.fetch("https://example.com"))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    private static RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(new JdkClientHttpRequestFactory());
    }
}
