package com.rag.tui.ui;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.client.ProviderClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides auto-completion candidates based on the current command context:
 * command names, module names for module-taking commands, and connect
 * subcommands/providers/models for the {@code connect} family.
 */
public class CommandCompletion implements CompletionCandidates {

    private static final List<String> CONNECT_SUBS =
            List.of("catalog", "chat", "embedding", "refresh");
    private static final long CACHE_TTL_MS = 30_000;

    private final CommandRegistry commandRegistry;
    private final ModuleRegistry moduleRegistry;
    private final ProviderClient providerClient;
    private volatile ModelCatalogDTO cachedCatalog;
    private volatile long cachedAtMs;

    public CommandCompletion(CommandRegistry commandRegistry, ModuleRegistry moduleRegistry,
                             ProviderClient providerClient) {
        this.commandRegistry = commandRegistry;
        this.moduleRegistry = moduleRegistry;
        this.providerClient = providerClient;
    }

    /** Pre-fetches the catalog off the prompt loop so the first keystroke is instant. */
    public void warmup() {
        CompletableFuture.supplyAsync(this::catalog);
    }

    @Override
    public List<String> candidates(String line, int cursor) {
        String prefix = line.substring(0, cursor);
        List<String> tokens = split(prefix);
        if (tokens.isEmpty()) return List.of();
        String command = tokens.get(0).toLowerCase();
        String token = currentToken(prefix);
        int pos = prefix.endsWith(" ") ? tokens.size() + 1 : tokens.size();
        return switch (command) {
            case "use", "start", "stop" -> prefixFilter(moduleNames(), token);
            case "connect" -> connectCandidates(tokens, token, pos);
            default -> pos == 1 ? prefixFilter(commandNames(), token) : List.of();
        };
    }

    private List<String> connectCandidates(List<String> tokens, String token, int pos) {
        if (pos == 1) return prefixFilter(commandNames(), token);
        if (pos == 2) return token.isEmpty() ? CONNECT_SUBS : containsFilter(connectFirst(), token);
        String sub = tokens.get(1).toLowerCase();
        if (pos == 3 && ("catalog".equals(sub) || "chat".equals(sub) || "embedding".equals(sub))) {
            return token.isEmpty() ? List.of() : containsFilter(providerIds(), token);
        }
        if (pos == 4 && ("chat".equals(sub) || "embedding".equals(sub))) {
            return token.isEmpty() ? List.of() : containsFilter(modelIds(tokens.get(2)), token);
        }
        return List.of();
    }

    private List<String> connectFirst() {
        var combined = new ArrayList<>(CONNECT_SUBS);
        combined.addAll(providerIds());
        return combined;
    }

    /** Splits the typed prefix into a List; empty tokens are dropped. */
    static List<String> split(String prefix) {
        var tokens = new ArrayList<String>();
        for (String raw : prefix.split(" ")) {
            if (!raw.isEmpty()) tokens.add(raw);
        }
        return tokens;
    }

    /** The not-yet-finished token (after the last space), possibly empty. */
    static String currentToken(String prefix) {
        int lastSpace = prefix.lastIndexOf(' ');
        return lastSpace < 0 ? prefix : prefix.substring(lastSpace + 1);
    }

    private List<String> commandNames() {
        return commandRegistry.filter("").stream()
                .map(CommandDescriptor::name)
                .toList();
    }

    private List<String> moduleNames() {
        return moduleRegistry.modules().stream()
                .map(m -> m.name())
                .toList();
    }

    private ModelCatalogDTO catalog() {
        long now = System.currentTimeMillis();
        if (now - cachedAtMs < CACHE_TTL_MS) return cachedCatalog;
        try {
            cachedCatalog = providerClient.catalog();
        } catch (RuntimeException e) {
            cachedCatalog = null;
        }
        cachedAtMs = now;
        return cachedCatalog;
    }

    private List<String> providerIds() {
        var models = catalog();
        if (models == null) return List.of();
        return models.models().stream()
                .map(m -> m.providerId())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> modelIds(String provider) {
        var models = catalog();
        if (models == null) return List.of();
        return models.models().stream()
                .filter(m -> m.providerId().equalsIgnoreCase(provider))
                .map(m -> m.modelId())
                .sorted()
                .toList();
    }

    private static List<String> prefixFilter(List<String> all, String token) {
        String lower = token.toLowerCase();
        if (lower.isEmpty()) return all;
        return all.stream()
                .filter(c -> c.toLowerCase().startsWith(lower))
                .toList();
    }

    private static List<String> containsFilter(List<String> all, String token) {
        String lower = token.toLowerCase();
        if (lower.isEmpty()) return all;
        return all.stream()
                .filter(c -> c.toLowerCase().contains(lower))
                .toList();
    }
}