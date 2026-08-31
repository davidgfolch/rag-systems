package com.rag.tui.fetching;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsoupWebPageFetcherTest {

    @Test
    void extractsTextFromFetchedPage() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.get()).thenReturn(Jsoup.parse(
                "<html><body><h1>Spring</h1><p>RAG retrieval augmented generation.</p></body></html>"));

        JsoupWebPageFetcher sut = new JsoupWebPageFetcher(connection);

        WebPage page = sut.fetch("https://example.com/spring");

        assertThat(page.url()).isEqualTo("https://example.com/spring");
        assertThat(page.text()).contains("RAG retrieval augmented generation");
        assertThat(page.text()).doesNotContain("<p>");
        verify(connection).get();
    }
}
