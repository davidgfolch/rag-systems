package com.rag.webcrawler.api;

import com.rag.contract.model.DiscoverRequest;
import com.rag.webcrawler.services.discovery.UrlDiscoveryService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

class DiscoverControllerTest {

    private static final String START = "https://example.com/docs/";

    private final UrlDiscoveryService discovery = mock(UrlDiscoveryService.class);
    private final DiscoverController sut = new DiscoverController(discovery);

    @Test
    void discoversWithTheServerDefaultLimit() {
        when(discovery.discover(START)).thenReturn(List.of(START + "a", START + "b"));

        var res = sut.discover(new DiscoverRequest().url(URI.create(START)));

        assertThat(res.getStatusCode()).isEqualTo(OK);
        assertThat(res.getBody().getUrls()).containsExactly(START + "a", START + "b");
    }

    @Test
    void usesAnExplicitUrlLimit() {
        when(discovery.discover(START, 3)).thenReturn(List.of(START + "a"));

        var res = sut.discover(new DiscoverRequest().url(URI.create(START)).maxUrls(3));

        assertThat(res.getBody().getUrls()).containsExactly(START + "a");
    }

    @Test
    void rejectsARequestWithoutUrl() {
        var res = sut.discover(new DiscoverRequest());

        assertThat(res.getStatusCode()).isEqualTo(BAD_REQUEST);
        verifyNoInteractions(discovery);
    }

    @Test
    void mapsInvalidUrlToBadRequest() {
        var res = sut.handleInvalidUrl(new IllegalArgumentException("bad"));

        assertThat(res.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(res.getBody()).isEqualTo("bad");
    }
}
