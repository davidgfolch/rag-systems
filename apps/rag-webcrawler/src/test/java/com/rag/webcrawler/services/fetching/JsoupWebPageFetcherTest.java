package com.rag.webcrawler.services.fetching;

import com.rag.contract.model.Page;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

        Page page = sut.fetch("https://example.com/spring");

        assertThat(page.getUrl()).isEqualTo("https://example.com/spring");
        assertThat(page.getTitle()).isEqualTo("Spring");
        assertThat(page.getText()).contains("RAG retrieval augmented generation");
        assertThat(page.getText()).doesNotContain("<p>");
        assertThat(page.getLinks()).contains("https://example.com/guide.html");
        verify(connection).get();
    }
}