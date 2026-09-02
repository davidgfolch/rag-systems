package com.rag.tui.client;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;

/**
 * Checks a rag-* module's readiness via its Spring Actuator health endpoint.
 * Used after {@code start <module>} so the terminal can tell when the module
 * is actually accepting requests instead of failing on a connection refused.
 */
public class ModuleHealthClient {

    private static final long POLL_MILLIS = 500;
    private static final long PROGRESS_MILLIS = 5_000;

    private final RestClient.Builder builder;
    private final String healthPath;

    public ModuleHealthClient(RestClient.Builder builder) {
        this(builder, "/actuator/health");
    }

    public ModuleHealthClient(RestClient.Builder builder, String healthPath) {
        this.builder = builder;
        this.healthPath = healthPath;
    }

    /**
     * Polls {@code {baseUrl}/actuator/health} until it returns 200 or the timeout
     * elapses, reporting a progress line through {@code progress} roughly every
     * five seconds so a slow module never looks like a frozen terminal.
     * Never throws: a non-responsive module just yields {@code false}.
     */
    public boolean waitUntilUp(String baseUrl, long timeoutMs, Consumer<String> progress) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastReport = 0;
        while (System.currentTimeMillis() < deadline) {
            if (isUp(baseUrl)) return true;
            long now = System.currentTimeMillis();
            if (progress != null && now - lastReport >= PROGRESS_MILLIS) {
                lastReport = now;
                progress.accept("  waiting for %s to become ready (%ds left)%n"
                        .formatted(baseUrl, Math.max(0, (deadline - now) / 1000)));
            }
            sleep(POLL_MILLIS);
        }
        return isUp(baseUrl);
    }

    public boolean isUp(String baseUrl) {
        try {
            builder.clone().baseUrl(baseUrl).build().get().uri(healthPath).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HealthCheckInterrupted(e);
        }
    }

    public static class HealthCheckInterrupted extends RuntimeException {
        public HealthCheckInterrupted(Throwable cause) {
            super("Interrupted while waiting for module health", cause);
        }
    }
}