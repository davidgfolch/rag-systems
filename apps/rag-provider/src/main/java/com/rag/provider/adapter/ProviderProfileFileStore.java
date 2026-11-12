package com.rag.provider.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderProfileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * JSON-file-backed {@link ProviderProfileStore}. Profiles (including API keys)
 * are written to a single file so runtime configuration survives restarts.
 */
public class ProviderProfileFileStore implements ProviderProfileStore {

    private static final Logger log = LoggerFactory.getLogger(ProviderProfileFileStore.class);

    private final ObjectMapper om;
    private final Path file;

    public ProviderProfileFileStore(ObjectMapper om, Path file) {
        this.om = om;
        this.file = file;
    }

    @Override
    public List<ProviderProfile> load() {
        if (Files.notExists(file)) return List.of();
        try {
            return om.readValue(file.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("Could not read provider profiles from {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void save(List<ProviderProfile> profiles) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            om.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), profiles);
            log.info("Saved {} provider profile(s) to {}", profiles.size(), file);
        } catch (IOException e) {
            log.warn("Could not persist provider profiles to {}: {}", file, e.getMessage());
        }
    }
}