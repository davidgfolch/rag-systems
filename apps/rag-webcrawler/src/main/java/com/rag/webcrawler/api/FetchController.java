package com.rag.webcrawler.api;

import com.rag.contract.model.FetchLinksRequest;
import com.rag.contract.model.FetchRequest;
import com.rag.contract.model.PageDTO;
import com.rag.webcrawler.services.WebCrawlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fetch")
public class FetchController {

    private static final Logger log = LoggerFactory.getLogger(FetchController.class);

    private final WebCrawlService webCrawlService;

    public FetchController(WebCrawlService webCrawlService) {
        this.webCrawlService = webCrawlService;
    }

    @PostMapping
    public ResponseEntity<PageDTO> fetch(@RequestBody FetchRequest request) {
        log.info("Fetch request for {}", request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(webCrawlService.fetch(request.getUrl().toString()));
    }

    @PostMapping("/links")
    public ResponseEntity<List<PageDTO>> fetchLinks(@RequestBody FetchLinksRequest request) {
        log.info("Fetch-links request for {}", request.getUrl());
        List<PageDTO> pages = webCrawlService.fetchRelevantLinks(
                request.getUrl().toString(), request.getQuestion(), 5);
        return ResponseEntity.status(HttpStatus.CREATED).body(pages);
    }
}