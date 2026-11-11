package com.rag.common.app.pipeline;

import com.rag.contract.pipeline.PipelineVariantDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.rag.contract.constants.ApiPaths.MODULE_VARIANTS;
import static com.rag.contract.constants.ApiPaths.MODULE_VARIANTS_ACTIVE;
import static com.rag.contract.constants.ApiPaths.VARIANT_ACTIVATE;
import static com.rag.contract.constants.ApiPaths.VARIANT_BY_ID;
import static org.springframework.http.HttpStatus.CREATED;

/**
 * Module-level variant switcher. A module can list, register and activate its
 * pipeline variants without affecting other modules or the global chat model.
 * The path module must match the module this process serves.
 */
@RestController
public class PipelineVariantController {

    private final VariantStore store;

    public PipelineVariantController(VariantStore store) {
        this.store = store;
    }

    @GetMapping(MODULE_VARIANTS)
    public ResponseEntity<List<PipelineVariantDTO>> list(@PathVariable String module) {
        return moduleMismatch(module) ? notFound() : ResponseEntity.ok(store.variants());
    }

    @GetMapping(MODULE_VARIANTS_ACTIVE)
    public ResponseEntity<PipelineVariantDTO> active(@PathVariable String module) {
        if (moduleMismatch(module)) {
            return notFound();
        }
        return store.active().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(VARIANT_BY_ID)
    public ResponseEntity<PipelineVariantDTO> get(@PathVariable String module, @PathVariable String variantId) {
        if (moduleMismatch(module)) {
            return notFound();
        }
        return store.find(variantId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(MODULE_VARIANTS)
    public ResponseEntity<PipelineVariantDTO> add(@PathVariable String module,
                                                  @RequestBody PipelineVariantDTO req) {
        if (moduleMismatch(module)) {
            return notFound();
        }
        try {
            return ResponseEntity.status(CREATED).body(store.add(req));
        } catch (IllegalArgumentException _) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping(VARIANT_BY_ID)
    public ResponseEntity<PipelineVariantDTO> update(@PathVariable String module,
                                                     @PathVariable String variantId,
                                                     @RequestBody PipelineVariantDTO req) {
        if (moduleMismatch(module) || store.find(variantId).isEmpty()) {
            return notFound();
        }
        try {
            return ResponseEntity.ok(store.update(variantId, req));
        } catch (IllegalArgumentException _) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(VARIANT_ACTIVATE)
    public ResponseEntity<PipelineVariantDTO> activate(@PathVariable String module,
                                                       @PathVariable String variantId) {
        if (moduleMismatch(module) || store.find(variantId).isEmpty()) {
            return notFound();
        }
        return ResponseEntity.ok(store.activate(variantId));
    }

    @DeleteMapping(VARIANT_BY_ID)
    public ResponseEntity<Void> delete(@PathVariable String module, @PathVariable String variantId) {
        if (moduleMismatch(module) || !store.remove(variantId)) {
            return notFound();
        }
        return ResponseEntity.noContent().build();
    }

    private boolean moduleMismatch(String module) {
        return !store.module().equals(module);
    }

    private static <T> ResponseEntity<T> notFound() {
        return ResponseEntity.notFound().build();
    }
}
