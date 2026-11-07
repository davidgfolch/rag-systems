package com.rag.webcrawler.api;

import com.rag.contract.model.DiscoverRequest;
import com.rag.webcrawler.services.discovery.UrlDiscoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscoverControllerTest {

    private static final String START = "https://example.com/docs/";

    private final UrlDiscoveryService discovery = mock(UrlDiscoveryService.class);
    private final DiscoverController sut = new DiscoverController(discovery);

    @Test
    void discoversWithTheServerDefaultLimit() {
        when(discovery.discover(START)).thenReturn(List.of(START + "a", START + "b"));

        var response = sut.discover(new DiscoverRequest().url(URI.create(START)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUrls()).containsExactly(START + "a", START + "b");
    }

    @Test
    void usesAnExplicitUrlLimit() {
        when(discovery.discover(START, 3)).thenReturn(List.of(START + "a"));

        var response = sut.discover(new DiscoverRequest().url(URI.create(START)).maxUrls(3));

        assertThat(response.getBody().getUrls()).containsExactly(START + "a");
    }

    @Test
    void rejectsARequestWithoutUrl() {
        var response = sut.discover(new DiscoverRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(discovery);
    }

    @Test
    void mapsInvalidUrlToBadRequest() {
        var response = sut.handleInvalidUrl(new IllegalArgumentException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("bad");
    }
}
