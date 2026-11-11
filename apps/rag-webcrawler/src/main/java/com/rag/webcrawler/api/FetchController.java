package com.rag.webcrawler.api;

import com.rag.contract.model.FetchLinksRequest;
import com.rag.contract.model.FetchRequest;
import com.rag.contract.model.PageDTO;
import com.rag.webcrawler.services.WebCrawlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/fetch")
public class FetchController {

    private static final Logger log = LoggerFactory.getLogger(FetchController.class);
    private static final int DEFAULT_LINK_LIMIT = 5;

    private final WebCrawlService webCrawlService;

    public FetchController(WebCrawlService webCrawlService) {
        this.webCrawlService = webCrawlService;
    }

    @PostMapping
    public ResponseEntity<PageDTO> fetch(@RequestBody FetchRequest req) {
        log.info("Fetch request for {}", req.getUrl());
        return ResponseEntity.status(CREATED).body(webCrawlService.fetch(req.getUrl().toString()));
    }

    @PostMapping("/links")
    public ResponseEntity<List<PageDTO>> fetchLinks(@RequestBody FetchLinksRequest req) {
        log.info("Fetch-links request for {}", req.getUrl());
        List<PageDTO> pages = webCrawlService.fetchRelevantLinks(
                req.getUrl().toString(), req.getQuestion(), DEFAULT_LINK_LIMIT);
        return ResponseEntity.status(CREATED).body(pages);
    }
}