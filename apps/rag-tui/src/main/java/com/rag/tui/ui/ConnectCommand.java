package com.rag.tui.ui;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.tui.client.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Renders the {@code connect} command family: provider status, catalog browse,
 * chat/embedding model switching and manual catalog refresh. When arguments are
 * omitted, the provider and/or model are chosen interactively via {@link Prompter}.
 */
public class ConnectCommand {

    private static final Logger log = LoggerFactory.getLogger(ConnectCommand.class);

    static final String USAGE = "Usage: connect [catalog [<provider>] | chat [<provider> [<model>]]"
            + " | embedding [<provider> [<model>]] | refresh]";

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
            default -> USAGE;
        };
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
        log.info("Connect status rendered: chatProvider={}, chatModel={}, embeddingProvider={}, embeddingModel={}",
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
        log.info("Connect catalog rendered: {} models", models.size());
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
        client.switchChat(provider, model);
        return success("Chat model switched: " + provider + "/" + model);
    }

    private String switchEmbedding(String provider, String model) {
        if (provider.isEmpty() || model.isEmpty()) {
            var chosen = chooseProviderAndModel("embedding", provider, model);
            if (chosen.isEmpty()) return "";
            provider = chosen.get().providerId();
            model = chosen.get().model();
        }
        client.switchEmbedding(provider, model);
        return success("Embedding model switched: " + provider + "/" + model);
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
