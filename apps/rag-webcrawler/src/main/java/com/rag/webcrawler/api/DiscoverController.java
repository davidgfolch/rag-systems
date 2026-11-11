package com.rag.webcrawler.api;

import com.rag.contract.model.DiscoverRequest;
import com.rag.contract.model.DiscoverResponse;
import com.rag.webcrawler.services.discovery.UrlDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static com.rag.contract.constants.ApiPaths.DISCOVER;

/**
 * Discovery-only endpoint: returns the crawlable URLs for a start page so the
 * rag-module can seed its frontier before asking per-page questions.
 */
@RestController
@RequestMapping(DISCOVER)
public class DiscoverController {

    private static final Logger log = LoggerFactory.getLogger(DiscoverController.class);

    private final UrlDiscoveryService discovery;

    public DiscoverController(UrlDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @PostMapping
    public ResponseEntity<DiscoverResponse> discover(@RequestBody DiscoverRequest req) {
        var url = req.getUrl();
        if (Objects.toString(url, "").isBlank()) return ResponseEntity.badRequest().build();
        var urls = req.getMaxUrls() == null
                ? discovery.discover(url.toString())
                : discovery.discover(url.toString(), req.getMaxUrls());
        log.info("Discovery request for {} returned {} URLs", url, urls.size());
        return ResponseEntity.ok(new DiscoverResponse().urls(urls));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidUrl(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
