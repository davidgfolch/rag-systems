package com.rag.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rag.provider.domain.DiscoveryCacheEntry;
import com.rag.provider.domain.DiscoveryCacheSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.Optional;

/**
 * JSON-file cache of the last verified host per service kind. Positive entries
 * live for the configured TTL; negative entries ({@code host == null}) expire
 * quickly so a server that just came up is retried soon. Writes are atomic and
 * the file is only a speed-up: unreadable or malformed content is ignored.
 */
public class DiscoveryCacheStore {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryCacheStore.class);
    private static final int CACHE_VERSION = 1;
    private static final String VERSION = "version";
    private static final String ENTRIES = "entries";
    private static final String HOST = "host";
    private static final String TIMESTAMP = "ts";
    private static final String TEMP_PREFIX = "discovery-cache-";
    private static final String TEMP_SUFFIX = ".tmp";

    private final ObjectMapper mapper;
    private final DiscoveryCacheSettings settings;
    private final Clock clock;
    private final Object lock = new Object();

    public DiscoveryCacheStore(ObjectMapper mapper, DiscoveryCacheSettings settings, Clock clock) {
        this.mapper = mapper;
        this.settings = settings;
        this.clock = clock;
    }

    /** Fresh entry for {@code kind}, or empty when missing, malformed or expired. */
    public Optional<DiscoveryCacheEntry> read(String kind) {
        synchronized (lock) {
            var node = load().path(ENTRIES).path(kind);
            if (!node.isObject()) return Optional.empty();
            var host = node.hasNonNull(HOST) ? node.get(HOST).asText() : null;
            var checkedAt = node.path(TIMESTAMP).asLong(0L);
            var ttl = host == null ? settings.negativeTtl() : settings.positiveTtl();
            var age = clock.instant().getEpochSecond() - checkedAt;
            return age > ttl.toSeconds() ? Optional.empty() : Optional.of(new DiscoveryCacheEntry(host, checkedAt));
        }
    }

    /** Stores {@code host}, or a negative entry when {@code host} is null. */
    public void write(String kind, String host) {
        synchronized (lock) {
            var root = load();
            var entries = entries(root);
            var entry = entries.putObject(kind);
            if (host == null) {
                entry.putNull(HOST);
            } else {
                entry.put(HOST, host);
            }
            entry.put(TIMESTAMP, clock.instant().getEpochSecond());
            save(root);
        }
    }

    /** Removes a stale entry so the next resolution scans again. */
    public void clear(String kind) {
        synchronized (lock) {
            var root = load();
            if (entries(root).remove(kind) != null) save(root);
        }
    }

    private ObjectNode load() {
        if (Files.notExists(settings.file())) return empty();
        try {
            var node = mapper.readTree(settings.file().toFile());
            return node instanceof ObjectNode object ? object : empty();
        } catch (IOException e) {
            log.debug("Could not read discovery cache {}: {}", settings.file(), e.getMessage());
            return empty();
        }
    }

    private ObjectNode empty() {
        var root = mapper.createObjectNode();
        root.put(VERSION, CACHE_VERSION);
        root.set(ENTRIES, mapper.createObjectNode());
        return root;
    }

    private static ObjectNode entries(ObjectNode root) {
        return root.has(ENTRIES) && root.get(ENTRIES) instanceof ObjectNode existing
                ? existing
                : root.putObject(ENTRIES);
    }

    private void save(ObjectNode root) {
        var file = settings.file();
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            var directory = file.getParent() == null ? Path.of(".") : file.getParent();
            var temp = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX);
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), root);
            move(temp, file);
        } catch (IOException e) {
            log.warn("Could not write discovery cache {}: {}", file, e.getMessage());
        }
    }

    private static void move(Path temp, Path file) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
