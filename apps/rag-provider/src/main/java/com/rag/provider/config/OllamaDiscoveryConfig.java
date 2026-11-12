package com.rag.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.provider.adapter.DiscoveryCacheStore;
import com.rag.provider.adapter.LanScanner;
import com.rag.provider.adapter.OllamaProbe;
import com.rag.provider.domain.DiscoveryCacheSettings;
import com.rag.provider.domain.OllamaDiscoverySettings;
import com.rag.provider.services.OllamaDiscoveryService;
import com.rag.provider.services.OllamaEndpointResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Wires Ollama LAN discovery. Everything is lazy: no socket is opened until an
 * {@code auto} spec is resolved, so explicit {@code OLLAMA_BASE_URL} values
 * keep booting offline.
 */
@Configuration
public class OllamaDiscoveryConfig {

    private static final String SUBNET_SEPARATOR = ",";

    @Bean
    public OllamaDiscoverySettings ollamaDiscoverySettings(
            @Value("${rag.provider.ollama.discovery.enabled:true}") boolean enabled,
            @Value("${rag.provider.ollama.discovery.port:11434}") int port,
            @Value("${rag.provider.ollama.discovery.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${rag.provider.ollama.discovery.probe-timeout-ms:3000}") long probeTimeoutMs,
            @Value("${rag.provider.ollama.discovery.max-hosts:1024}") int maxHosts) {
        return new OllamaDiscoverySettings(enabled, port,
                Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(probeTimeoutMs), maxHosts);
    }

    @Bean
    public DiscoveryCacheSettings discoveryCacheSettings(
            @Value("${rag.provider.ollama.discovery.cache-file:data/discovery/cache.json}") String cacheFile,
            @Value("${rag.provider.ollama.discovery.positive-ttl-seconds:3600}") long positiveTtlSeconds,
            @Value("${rag.provider.ollama.discovery.negative-ttl-seconds:60}") long negativeTtlSeconds) {
        return new DiscoveryCacheSettings(Path.of(cacheFile),
                Duration.ofSeconds(positiveTtlSeconds), Duration.ofSeconds(negativeTtlSeconds));
    }

    @Bean
    public LanScanner lanScanner(OllamaDiscoverySettings settings,
                                 @Value("${rag.provider.ollama.discovery.subnets:}") String subnets) {
        return new LanScanner(settings, parseSubnets(subnets), LanScanner::detectLocalAddresses);
    }

    @Bean
    public OllamaProbe ollamaProbe(OllamaDiscoverySettings settings) {
        return new OllamaProbe(settings.probeTimeout());
    }

    @Bean
    public DiscoveryCacheStore discoveryCacheStore(ObjectMapper om, DiscoveryCacheSettings settings) {
        return new DiscoveryCacheStore(om, settings, Clock.systemUTC());
    }

    @Bean
    public OllamaDiscoveryService ollamaDiscoveryService(OllamaDiscoverySettings settings, LanScanner scanner,
                                                         OllamaProbe probe, DiscoveryCacheStore cache) {
        return new OllamaDiscoveryService(settings, scanner, probe, cache);
    }

    @Bean
    public OllamaEndpointResolver ollamaEndpointResolver(OllamaDiscoverySettings settings,
                                                         OllamaDiscoveryService discovery) {
        return new OllamaEndpointResolver(settings, discovery);
    }

    static List<String> parseSubnets(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(SUBNET_SEPARATOR))
                .map(String::strip)
                .filter(subnet -> !subnet.isEmpty())
                .toList();
    }
}
