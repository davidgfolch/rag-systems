package com.rag.tui.ui;

import com.rag.contract.constants.ApiPaths;
import com.rag.contract.pipeline.ChunkingStrategy;
import com.rag.contract.pipeline.PipelineVariantDTO;
import com.rag.tui.client.ProviderClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.rag.tui.ui.TerminalStyle.error;
import static com.rag.tui.ui.TerminalStyle.info;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Lists, creates and activates the pipeline variants of the active module. A
 * variant bundles the chunking strategy/window with its own vector table inside
 * the module schema; switching one never restarts the module.
 */
public class VariantCommands {

    private static final String ADD = "add";
    private static final String PICK_TITLE = "Switch pipeline variant";
    private static final String STRATEGY_TITLE = "Chunking strategy";
    private static final int CHARS_PER_TOKEN = 4;

    private final ModuleRegistry registry;
    private final RagApiClient api;
    private final Prompter prompter;
    private final ProviderClient provider;

    public VariantCommands(ModuleRegistry registry, RagApiClient api, Prompter prompter) {
        this(registry, api, prompter, null);
    }

    public VariantCommands(ModuleRegistry registry, RagApiClient api, Prompter prompter,
                           ProviderClient provider) {
        this.registry = registry;
        this.api = api;
        this.prompter = prompter;
        this.provider = provider;
    }

    public String switchVariant(String arg) {
        var module = registry.active().name();
        var variants = api.variants(module);
        var tokens = tokens(arg);
        if (!tokens.isEmpty() && ADD.equalsIgnoreCase(tokens.getFirst())) {
            return create(module, tokens.subList(1, tokens.size()), variants);
        }
        if (variants.isEmpty()) {
            return info("No pipeline variants registered on " + module + ". Create one with: variant add "
                    + "<strategy> [<size>] [<overlap>] (or POST " + ApiPaths.moduleVariants(module) + ").");
        }
        var chosen = tokens.isEmpty()
                ? prompter.pick(PICK_TITLE, choices(variants))
                : Optional.of(tokens.getFirst());
        return chosen.map(variantId -> activate(module, variantId, variants)).orElse("");
    }

    private String create(String module, List<String> args, List<PipelineVariantDTO> variants) {
        var template = embeddingTemplate(module, variants);
        if (template.isEmpty()) {
            return error("No variant to inherit the embedding model from; start the module so it seeds one.");
        }
        var picked = args.isEmpty() ? prompter.pick(STRATEGY_TITLE, strategyChoices()) : Optional.of(args.getFirst());
        if (picked.isEmpty()) {
            return "";
        }
        var strategy = ChunkingStrategy.from(picked.get());
        if (strategy.isEmpty()) {
            return error("Unknown strategy '" + picked.get() + "'. Choose RECURSIVE, FIXED, TOKEN or SEMANTIC.");
        }
        var size = intArg(args, 1, "Chunk size [" + template.get().chunkSize() + "]: ",
                template.get().chunkSize());
        var overlap = intArg(args, 2, "Chunk overlap [" + template.get().chunkOverlap() + "]: ",
                template.get().chunkOverlap());
        if (size.isEmpty() || overlap.isEmpty()) {
            return error("Chunk size and overlap must be whole numbers.");
        }
        var problem = windowProblem(strategy.get(), size.getAsInt(), overlap.getAsInt());
        if (problem != null) {
            return error(problem);
        }
        var request = new PipelineVariantDTO(null, module, strategy.get().name(),
                template.get().embeddingModel(), template.get().embeddingDimension(), size.getAsInt(), overlap.getAsInt());
        var created = api.createVariant(module, request);
        return success("Variant created on " + module + ": " + created.variantId() + " ("
                + created.strategy() + " " + created.chunkSize() + "/" + created.chunkOverlap()
                + "). Activate it with: variant " + created.variantId());
    }

    /**
     * New variants embed with the provider's active embedding model (so the
     * label is a real model id), falling back to the active variant when
     * rag-provider is unreachable. The chunk window comes from that variant.
     */
    private Optional<PipelineVariantDTO> embeddingTemplate(String module, List<PipelineVariantDTO> variants) {
        var active = api.activeVariant(module).or(() -> variants.stream().findFirst());
        if (provider == null) {
            return active;
        }
        try {
            var status = provider.status();
            if (status == null || status.embedding() == null || status.embedding().model() == null
                    || status.embedding().model().isBlank()) {
                return active;
            }
            return Optional.of(new PipelineVariantDTO(null, module, ChunkingStrategy.RECURSIVE.name(),
                    status.embedding().model(), status.embeddingDimension(),
                    active.map(PipelineVariantDTO::chunkSize).orElse(512),
                    active.map(PipelineVariantDTO::chunkOverlap).orElse(128)));
        } catch (RuntimeException _) {
            return active;
        }
    }

    private String activate(String module, String variantId, List<PipelineVariantDTO> variants) {
        var known = variants.stream().anyMatch(variant -> variant.variantId().equals(variantId));
        if (!known) {
            return error("Unknown variant: " + variantId);
        }
        var active = api.activateVariant(module, variantId);
        return success("Variant active on " + module + ": " + active.strategy() + " "
                + active.chunkSize() + "/" + active.chunkOverlap() + ", " + active.embeddingModel()
                + " " + active.embeddingDimension() + "d");
    }

    private OptionalInt intArg(List<String> args, int index, String promptText, int fallback) {
        if (args.size() > index) {
            return parse(args.get(index));
        }
        var raw = prompter.prompt(promptText);
        return raw == null || raw.isBlank() ? OptionalInt.of(fallback) : parse(raw);
    }

    private static OptionalInt parse(String raw) {
        try {
            return OptionalInt.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException _) {
            return OptionalInt.empty();
        }
    }

    private static String windowProblem(ChunkingStrategy strategy, int size, int overlap) {
        if (size <= 0 || overlap < 0 || overlap >= size) {
            return "Chunk window must satisfy size > 0 and 0 <= overlap < size.";
        }
        if (strategy == ChunkingStrategy.TOKEN
                && size / CHARS_PER_TOKEN <= overlap / CHARS_PER_TOKEN) {
            return "Token variants need size / 4 to exceed overlap / 4 (e.g. 256/64).";
        }
        return null;
    }

    private static List<Prompter.Choice> strategyChoices() {
        return Arrays.stream(ChunkingStrategy.values())
                .map(strategy -> new Prompter.Choice(strategy.name(), strategy.name(), ""))
                .toList();
    }

    private static List<Prompter.Choice> choices(List<PipelineVariantDTO> variants) {
        return variants.stream()
                .map(variant -> new Prompter.Choice(variant.variantId() + " " + variant.strategy(),
                        variant.variantId(),
                        variant.chunkSize() + "/" + variant.chunkOverlap() + " · " + variant.embeddingModel()
                                + " " + variant.embeddingDimension() + "d"))
                .toList();
    }

    private static List<String> tokens(String arg) {
        return arg == null || arg.isBlank() ? List.of() : List.of(arg.trim().split("\\s+"));
    }
}
