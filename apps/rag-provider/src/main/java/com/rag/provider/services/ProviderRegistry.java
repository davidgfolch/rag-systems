package com.rag.provider.services;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the connection profiles that {@link ModelRouter} can switch between.
 * Seeded from environment defaults and extended at runtime via
 * {@code POST /api/provider/configure}.
 */
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    private final Map<String, ProviderProfile> profiles = new LinkedHashMap<>();

    public ProviderRegistry(List<ProviderProfile> defaults) {
        defaults.forEach(profile -> {
            profiles.put(profile.id(), profile);
            log.info("Registered provider profile '{}' ({})", profile.id(), profile.type());
        });
    }

    public List<ProviderProfile> all() {
        return List.copyOf(profiles.values());
    }

    public Optional<ProviderProfile> find(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public ProviderProfile save(ConfigureProviderRequest request) {
        var profile = new ProviderProfile(request.providerId(), toType(request.type()),
                request.displayName(), request.baseUrl(), request.apiKey());
        profiles.put(profile.id(), profile);
        log.info("Saved provider profile '{}' as {}", profile.id(), profile.type());
        return profile;
    }

    private static ProviderType toType(String type) {
        return (type == null || type.isBlank())
                ? ProviderType.OPENAI_COMPATIBLE
                : ProviderType.valueOf(type.trim().toUpperCase());
    }
}