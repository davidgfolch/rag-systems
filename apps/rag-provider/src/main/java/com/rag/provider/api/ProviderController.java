package com.rag.provider.api;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.provider.services.ModelCatalogService;
import com.rag.provider.services.ModelRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the provider service's configuration surface: status, chat/embedding
 * model switching, and provider profile management.
 */
@RestController
@RequestMapping("/api/provider")
public class ProviderController {

    private static final Logger log = LoggerFactory.getLogger(ProviderController.class);

    private final ModelRouter router;
    private final ModelCatalogService catalogService;

    public ProviderController(ModelRouter router, ModelCatalogService catalogService) {
        this.router = router;
        this.catalogService = catalogService;
    }

    @GetMapping
    public ProviderStatusDTO status() {
        log.info("Provider status requested");
        return router.status();
    }

    @PostMapping("/chat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void switchChat(@RequestBody ModelSpecDTO request) {
        warnIfNotInCatalog(request);
        router.switchChat(request);
        log.info("Chat model switch requested: providerId={}, model={}",
                request.providerId(), request.model());
    }

    @PostMapping("/embedding")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void switchEmbedding(@RequestBody ModelSpecDTO request) {
        warnIfNotInCatalog(request);
        router.switchEmbedding(request);
        log.info("Embedding model switch requested: providerId={}, model={}",
                request.providerId(), request.model());
    }

    @PostMapping("/configure")
    public ProviderStatusDTO configure(@RequestBody ConfigureProviderRequest request) {
        router.configure(request);
        log.info("Provider profile configured: providerId={}", request.providerId());
        return router.status();
    }

    private void warnIfNotInCatalog(ModelSpecDTO request) {
        if (!catalogService.isKnown(request.providerId(), request.model())) {
            log.warn("Model {} on provider {} is not listed in the catalog (advisory; switch allowed)",
                    request.model(), request.providerId());
        }
    }
}