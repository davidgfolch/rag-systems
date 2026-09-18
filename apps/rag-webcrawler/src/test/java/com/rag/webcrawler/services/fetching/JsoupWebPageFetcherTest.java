package com.rag.webcrawler.services.fetching;

import com.rag.contract.model.PageDTO;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsoupWebPageFetcherTest {

    @Test
    void extractsTextTitleAndLinks() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.get()).thenReturn(Jsoup.parse(
                "<html><head><title>Spring</title></head><body>"
                        + "<p>RAG retrieval augmented generation.</p>"
                        + "<a href=\"/guide.html\">Guide</a></body></html>",
                "https://example.com/spring"));
        JsoupWebPageFetcher sut = new JsoupWebPageFetcher(connection);
        PageDTO page = sut.fetch("https://example.com/spring");
        assertThat(page.getUrl()).isEqualTo("https://example.com/spring");
        assertThat(page.getTitle()).isEqualTo("Spring");
        assertThat(page.getText()).contains("RAG retrieval augmented generation");
        assertThat(page.getText()).doesNotContain("<p>");
        assertThat(page.getLinks()).contains("https://example.com/guide.html");
        verify(connection).get();
    }

    @Test
    void wrapsIoFailureInWebFetchException() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.get()).thenThrow(new IOException("refused"));
        JsoupWebPageFetcher sut = new JsoupWebPageFetcher(connection);
        assertThatThrownBy(() -> sut.fetch("https://example.com/x"))
                .isInstanceOf(JsoupWebPageFetcher.WebFetchException.class)
                .hasMessage("Failed to fetch URL: https://example.com/x")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void fetchesOverHttpWithDefaultConstructor() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = ("<html><head><title>Local</title></head><body><p>hello</p>"
                    + "<a href=\"/next\">next</a></body></html>").getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            PageDTO page = new JsoupWebPageFetcher().fetch(url);
            assertThat(page.getTitle()).isEqualTo("Local");
            assertThat(page.getText()).contains("hello");
        } finally {
            server.stop(0);
        }
    }
}