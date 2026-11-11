package com.rag.common.ingestion.web;

import com.rag.common.core.resilience.RetryObserver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebCrawlerClientFactoryTest {

    @Test
    void shouldBuildClientWithConfiguredBaseUrlAndTimeouts() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/fetch", exchange -> {
            var body = """
                    {"url":"https://example.com","title":"Example","text":"page text"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var sut = WebCrawlerClient.create(RestClient.builder(), baseUrl, RetryObserver.NOOP);

            var page = sut.fetch("https://example.com");

            assertThat(page.getTitle()).isEqualTo("Example");
            assertThat(WebCrawlerClient.DEFAULT_CONNECT_TIMEOUT).isPositive();
            assertThat(WebCrawlerClient.DEFAULT_READ_TIMEOUT).isPositive();
        } finally {
            server.stop(0);
        }
    }
}
