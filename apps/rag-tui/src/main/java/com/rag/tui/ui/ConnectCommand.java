package com.rag.tui.ui;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.tui.client.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Renders the {@code connect} command family: provider status, catalog browse,
 * chat/embedding model switching and manual catalog refresh.
 */
public class ConnectCommand {

    private static final Logger log = LoggerFactory.getLogger(ConnectCommand.class);

    static final String USAGE = "Usage: connect [catalog [<provider>] | chat <provider> <model>"
            + " | embedding <provider> <model> | refresh]";

    private final ProviderClient client;

    public ConnectCommand(ProviderClient client) {
        this.client = client;
    }

    public String execute(String arg) {
        if (arg.isEmpty()) return status();
        var parts = arg.split("\\s+", 3);
        return switch (parts[0]) {
            case "catalog" -> catalog(parts.length > 1 ? parts[1] : "");
            case "chat" -> chat(parts);
            case "embedding" -> embedding(parts);
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

    private String chat(String[] parts) {
        if (parts.length < 3) return "Usage: connect chat <provider> <model>";
        client.switchChat(parts[1], parts[2]);
        return success("Chat model switched: " + parts[1] + "/" + parts[2]);
    }

    private String embedding(String[] parts) {
        if (parts.length < 3) return "Usage: connect embedding <provider> <model>";
        client.switchEmbedding(parts[1], parts[2]);
        return success("Embedding model switched: " + parts[1] + "/" + parts[2]);
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