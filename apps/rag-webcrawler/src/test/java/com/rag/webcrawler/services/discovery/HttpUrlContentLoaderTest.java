package com.rag.webcrawler.services.discovery;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class HttpUrlContentLoaderTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpUrlContentLoader sut = new HttpUrlContentLoader();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void loadsPlainText() {
        respond("/robots.txt", 200, "User-agent: *".getBytes(StandardCharsets.UTF_8));

        assertThat(sut.load(baseUrl + "/robots.txt"))
                .hasValueSatisfying(bytes ->
                        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("User-agent: *"));
    }

    @Test
    void decodesGzipBodies() throws IOException {
        respond("/sitemap.gz", 200, gzip("<urlset/>"));

        assertThat(sut.load(baseUrl + "/sitemap.gz"))
                .hasValueSatisfying(bytes ->
                        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("<urlset/>"));
    }

    @Test
    void returnsEmptyForErrorStatus() {
        respond("/missing.txt", 404, new byte[0]);

        assertThat(sut.load(baseUrl + "/missing.txt")).isEmpty();
    }

    @Test
    void returnsEmptyForInvalidUrl() {
        assertThat(sut.load("ftp://example.com/file")).isEmpty();
        assertThat(sut.load("not a url")).isEmpty();
    }

    private void respond(String path, int status, byte[] body) {
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }

    private static byte[] gzip(String text) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
