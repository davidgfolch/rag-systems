package com.rag.provider.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.provider.domain.ModelCapabilities;
import com.rag.provider.domain.ModelCatalogPort;
import com.rag.provider.domain.ModelCost;
import com.rag.provider.domain.ModelInfo;
import com.rag.provider.domain.ModelLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches the models.dev {@code api.json} catalog over HTTP and maps it onto
 * domain {@link ModelInfo} entries. Parsing is defensive: missing sections are
 * defaulted and entries that cannot be read are skipped, so upstream schema
 * drift degrades gracefully instead of failing the fetch.
 */
public class ModelsDevCatalogClient implements ModelCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(ModelsDevCatalogClient.class);

    private final String source;
    private final RestClient restClient;
    private final ObjectMapper om;

    public ModelsDevCatalogClient(String source, RestClient restClient, ObjectMapper om) {
        this.source = source;
        this.restClient = restClient;
        this.om = om;
    }

    @Override
    public List<ModelInfo> fetch() {
        String body;
        try {
            body = restClient.get().retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to fetch model catalog from " + source, e);
        }
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            var models = parse(om, body);
            log.info("Fetched {} models from {}", models.size(), source);
            return models;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse model catalog from " + source, e);
        }
    }

    static List<ModelInfo> parse(ObjectMapper om, String json) throws JsonProcessingException {
        var root = om.readTree(json);
        var models = new ArrayList<ModelInfo>();
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            collectModels(entry.getValue(), entry.getKey(), models);
        }
        return models;
    }

    private static void collectModels(JsonNode providerNode, String providerId, List<ModelInfo> out) {
        var modelsNode = providerNode.path("models");
        if (!modelsNode.isObject()) {
            return;
        }
        String baseUrl = providerNode.path("api").asText(null);
        String apiKeyEnv = firstEnvVar(providerNode.path("env"));
        for (Map.Entry<String, JsonNode> modelEntry : modelsNode.properties()) {
            out.add(model(providerId, modelEntry.getKey(), modelEntry.getValue(), baseUrl, apiKeyEnv));
        }
    }

    private static String firstEnvVar(JsonNode env) {
        return env.isArray() && !env.isEmpty() ? env.get(0).asText(null) : null;
    }

    private static ModelInfo model(String providerId, String modelId, JsonNode node,
                                   String baseUrl, String apiKeyEnv) {
        var limit = node.path("limit");
        var cost = node.path("cost");
        return new ModelInfo(
                providerId,
                modelId,
                node.path("name").asText(modelId),
                new ModelLimits(limit.path("context").asLong(), limit.path("output").asLong()),
                new ModelCapabilities(
                        node.path("reasoning").asBoolean(),
                        node.path("tool_call").asBoolean(),
                        node.path("structured_output").asBoolean()),
                new ModelCost(cost.path("input").asDouble(), cost.path("output").asDouble()),
                node.path("status").asText(null),
                baseUrl,
                apiKeyEnv);
    }
}