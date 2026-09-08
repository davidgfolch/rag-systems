package com.rag.provider.api;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.provider.services.ModelCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the refreshable model catalog: browsing ({@code GET}) and an explicit
 * refresh trigger ({@code POST}) used by the TUI connect flow.
 */
@RestController
@RequestMapping("/api/provider/catalog")
public class CatalogController {

    private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

    private final ModelCatalogService catalogService;

    public CatalogController(ModelCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ModelCatalogDTO catalog() {
        var catalog = catalogService.catalog();
        log.info("Model catalog requested: {} models from {}", catalog.models().size(), catalog.source());
        return catalog;
    }

    @PostMapping("/refresh")
    public ModelCatalogDTO refresh() {
        var catalog = catalogService.refresh();
        log.info("Model catalog refresh completed: {} models from {}", catalog.models().size(), catalog.source());
        return catalog;
    }
}