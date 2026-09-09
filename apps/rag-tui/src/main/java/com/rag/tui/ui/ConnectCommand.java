package com.rag.tui.ui;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.tui.client.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

import static com.rag.tui.ui.TerminalStyle.error;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Renders the {@code connect} command family: provider status, catalog browse,
 * chat/embedding model switching and manual catalog refresh. When arguments are
 * omitted, the provider and/or model are chosen interactively via {@link Prompter}.
 */
public class ConnectCommand {

    private static final Logger log = LoggerFactory.getLogger(ConnectCommand.class);

    static final String USAGE = "Usage: connect <catalog|chat|embedding|refresh> [args]\n"
            + "  connect catalog [<provider>]      browse the model catalog\n"
            + "  connect chat <provider> <model>   switch the chat model\n"
            + "  connect embedding <provider> <model>   switch the embedding model\n"
            + "  connect refresh                   refresh the model catalog";

    private final ProviderClient client;
    private final Prompter prompter;

    public ConnectCommand(ProviderClient client, Prompter prompter) {
        this.client = client;
        this.prompter = prompter;
    }

    public String execute(String arg) {
        if (arg.isEmpty()) return status();
        var parts = arg.split("\\s+", 3);
        return switch (parts[0]) {
            case "catalog" -> catalog(parts.length > 1 ? parts[1] : "");
            case "chat" -> switchChat(parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : "");
            case "embedding" -> switchEmbedding(parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : "");
            case "refresh" -> refresh();
            default -> switchChatByProvider(parts[0], parts.length > 1 ? parts[1] : "");
        };
    }

    /** Renders the active chat/embedding models (shown on TUI startup). */
    public String activeSpecs() {
        var status = client.status();
        return "Chat model: " + spec(status.chat())
                + "\nEmbedding model: " + spec(status.embedding())
                + " (dimension " + status.embeddingDimension() + ")";
    }

    private String switchChatByProvider(String provider, String model) {
        var catalog = client.catalog();
        boolean known = catalog != null && catalog.models().stream()
                .map(ProviderModelDTO::providerId)
                .distinct()
                .anyMatch(id -> id.equalsIgnoreCase(provider));
        if (!known) return USAGE;
        return switchChat(provider, model);
    }

    private String status() {
        ProviderStatusDTO status = client.status();
        ModelCatalogDTO catalog = client.catalog();
        var sb = new StringBuilder("Provider connection:")
                .append("\n - chat: ").append(spec(status.chat()))
                .append("\n - embedding: ").append(spec(status.embedding()))
                .append(" (dimension ").append(status.embeddingDimension()).append(")")
                .append("\n - catalog: ").append(catalog.source());
        if (catalog.fetchedAt() == null) {
            sb.append(" (not fetched yet)");
        } else {
            sb.append(String.format(" (fetched %s, %d models)", catalog.fetchedAt(), catalog.models().size()));
        }
        log.debug("Connect status rendered: chatProvider={}, chatModel={}, embeddingProvider={}, embeddingModel={}",
                status.chat().providerId(), status.chat().model(),
                status.embedding().providerId(), status.embedding().model());
        return sb.toString();
    }

    private String catalog(String providerFilter) {
        var models = client.catalog().models();
        if (!providerFilter.isEmpty()) {
            var lower = providerFilter.toLowerCase();
            models = models.stream()
                    .filter(m -> m.providerId().toLowerCase().startsWith(lower))
                    .toList();
        }
        if (models.isEmpty()) {
            return providerFilter.isEmpty()
                    ? "Catalog is empty. Try 'connect refresh'."
                    : "No models for provider '" + providerFilter + "'.";
        }
        var sb = new StringBuilder("Model catalog (").append(client.catalog().source()).append("):\n");
        var provider = "";
        for (ProviderModelDTO model : models) {
            if (!model.providerId().equals(provider)) {
                provider = model.providerId();
                sb.append(" ").append(provider).append(":\n");
            }
            sb.append("   - ").append(model.modelId());
            if (model.name() != null && !model.name().isEmpty()) {
                sb.append(" (").append(model.name()).append(")");
            }
            appendLimits(sb, model);
            appendCost(sb, model);
            appendCapabilities(sb, model);
            if (model.status() != null) {
                sb.append(" [").append(model.status()).append("]");
            }
            sb.append("\n");
        }
        log.debug("Connect catalog rendered: {} models", models.size());
        return sb.toString();
    }

    private static void appendLimits(StringBuilder sb, ProviderModelDTO model) {
        if (model.limits() != null) {
            sb.append(" - ctx ").append(model.limits().context());
            if (model.limits().output() > 0) {
                sb.append(", out ").append(model.limits().output());
            }
        }
    }

    private static void appendCost(StringBuilder sb, ProviderModelDTO model) {
        if (model.cost() != null
                && (model.cost().inputPerMillion() > 0 || model.cost().outputPerMillion() > 0)) {
            sb.append(" - $").append(model.cost().inputPerMillion())
                    .append("/").append(model.cost().outputPerMillion()).append(" per 1M");
        }
    }

    private static void appendCapabilities(StringBuilder sb, ProviderModelDTO model) {
        if (model.capabilities() != null) {
            if (model.capabilities().reasoning()) sb.append(" - reasoning");
            if (model.capabilities().toolCall()) sb.append(" - tool-call");
            if (model.capabilities().structuredOutput()) sb.append(" - structured-output");
        }
    }

    private String switchChat(String provider, String model) {
        if (provider.isEmpty() || model.isEmpty()) {
            var chosen = chooseProviderAndModel("chat", provider, model);
            if (chosen.isEmpty()) return "";
            provider = chosen.get().providerId();
            model = chosen.get().model();
        }
        final String prov = provider;
        final String mod = model;
        return runSwitch(() -> client.switchChat(prov, mod), provider,
                "Chat model switched: " + provider + "/" + model);
    }

    private String switchEmbedding(String provider, String model) {
        if (provider.isEmpty() || model.isEmpty()) {
            var chosen = chooseProviderAndModel("embedding", provider, model);
            if (chosen.isEmpty()) return "";
            provider = chosen.get().providerId();
            model = chosen.get().model();
        }
        final String prov = provider;
        final String mod = model;
        return runSwitch(() -> client.switchEmbedding(prov, mod), provider,
                "Embedding model switched: " + provider + "/" + model);
    }

    /**
     * Runs a switch, offering to register the provider at runtime when
     * rag-provider rejects it as unknown; the switch is retried once after a
     * successful registration.
     */
    private String runSwitch(Runnable action, String providerId, String successMessage) {
        boolean configured = false;
        while (true) {
            try {
                action.run();
                return success(successMessage);
            } catch (RestClientResponseException e) {
                String rejection = e.getResponseBodyAsString();
                if (!configured && rejection.contains("Unknown provider")) {
                    configured = true;
                    String setup = attemptConfigure(providerId);
                    if (setup == null) continue;
                    return setup;
                }
                log.warn("Switch rejected by rag-provider: {}", rejection);
                return error("Request rejected by rag-provider: "
                        + (rejection.isEmpty() ? e.getMessage() : rejection));
            } catch (RestClientException e) {
                log.warn("Provider module unreachable: {}", e.getMessage());
                return error("Provider module unreachable: " + e.getMessage());
            }
        }
    }

    /** Returns null when the switch should be retried, "" when cancelled, else an error text. */
    private String attemptConfigure(String providerId) {
        try {
            return new ConfigureProviderFlow(client, prompter).configure(providerId) ? null : "";
        } catch (RestClientException e) {
            log.warn("Provider module unreachable while configuring: {}", e.getMessage());
            return error("Provider module unreachable: " + e.getMessage());
        }
    }

    private Optional<ModelSpecDTO> chooseProviderAndModel(String kind, String provider, String model) {
        var models = client.catalog().models();
        if (provider.isEmpty()) {
            var providers = models.stream().map(ProviderModelDTO::providerId).distinct().sorted()
                    .map(id -> new Prompter.Choice(id, id))
                    .toList();
            provider = prompter.pick("Choose " + kind + " provider", providers).orElse("");
            if (provider.isEmpty()) return Optional.empty();
        }
        final String prov = provider;
        var choices = models.stream()
                .filter(m -> m.providerId().equalsIgnoreCase(prov))
                .map(m -> new Prompter.Choice(m.modelId(), m.modelId(),
                        m.name() == null ? "" : m.name()))
                .toList();
        if (model.isEmpty()) {
            if (choices.isEmpty()) return Optional.empty();
            model = prompter.pick("Choose " + kind + " model for " + prov, choices).orElse("");
            if (model.isEmpty()) return Optional.empty();
        }
        return Optional.of(new ModelSpecDTO(prov, model));
    }

    private String refresh() {
        var catalog = client.refreshCatalog();
        return success(String.format("Catalog refreshed: %d models from %s",
                catalog.models().size(), catalog.source()));
    }

    private static String spec(ModelSpecDTO spec) {
        return spec.providerId() + "/" + spec.model();
    }
}
