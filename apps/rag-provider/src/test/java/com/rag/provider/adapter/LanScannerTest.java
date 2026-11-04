package com.rag.provider.adapter;

import com.rag.provider.domain.OllamaDiscoverySettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanScannerTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(250);

    private static OllamaDiscoverySettings settings(int maxHosts) {
        return new OllamaDiscoverySettings(true, 11434, CONNECT_TIMEOUT,
                Duration.ofMillis(500), maxHosts);
    }

    @Test
    void shouldDeriveSubnetsFromSupplierAndExtras() {
        var sut = new LanScanner(settings(0), List.of("10.1.0.0/24"), () -> List.of("192.168.9.10"));
        assertThat(sut.subnets()).contains("192.168.9.0/24", "192.168.8.0/24", "192.168.10.0/24",
                "10.1.0.0/24", "192.168.1.0/24");
    }

    @Test
    void shouldFallBackToCommonSubnetsWhenSupplierReturnsNothing() {
        var sut = new LanScanner(settings(0), null, () -> null);
        assertThat(sut.subnets()).containsExactly("192.168.1.0/24", "192.168.0.0/24");
    }

    @Test
    void shouldReportOnlyReachableHosts() throws IOException {
        try (var server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            var sut = new LanScanner(settings(0), List.of(), List::of);
            assertThat(sut.scanHosts(List.of("127.0.0.1"), server.getLocalPort()))
                    .containsExactly("127.0.0.1");
        }
    }

    @Test
    void shouldSkipHostsWithClosedPort() throws IOException {
        int port;
        try (var server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            port = server.getLocalPort();
        }
        var sut = new LanScanner(settings(0), List.of(), List::of);
        assertThat(sut.scanHosts(List.of("127.0.0.1"), port)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyOrNullHostLists() {
        var sut = new LanScanner(settings(0), List.of(), List::of);
        assertThat(sut.scanHosts(List.of(), 11434)).isEmpty();
        assertThat(sut.scanHosts(null, 11434)).isEmpty();
    }

    @Test
    void shouldScanLoopbackWithTheHostBudget() throws IOException {
        try (var server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            var sut = new LanScanner(
                    new OllamaDiscoverySettings(true, server.getLocalPort(), CONNECT_TIMEOUT,
                            Duration.ofMillis(500), 1),
                    List.of(), () -> List.of("127.0.0.1"));
            assertThat(sut.scan()).containsExactly("127.0.0.1");
        }
    }
}
