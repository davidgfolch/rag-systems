package com.rag.provider.adapter;

import com.rag.provider.domain.Ipv4Networks;
import com.rag.provider.domain.OllamaDiscoverySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Discovers hosts that accept TCP connections on the Ollama port. The scan
 * covers the machine's own /24, its adjacent /24s, the common home LANs and
 * any configured CIDRs. Virtual threads keep ~750 probes cheap while a permit
 * cap bounds open sockets.
 */
public class LanScanner {

    private static final Logger log = LoggerFactory.getLogger(LanScanner.class);
    private static final int MAX_CONCURRENT = 100;
    private static final int MAX_TIMEOUT_MS = Integer.MAX_VALUE;
    private static final int MIN_TIMEOUT_MS = 1;

    private final OllamaDiscoverySettings settings;
    private final List<String> extraSubnets;
    private final Supplier<List<String>> localAddresses;

    public LanScanner(OllamaDiscoverySettings settings, List<String> extraSubnets,
                      Supplier<List<String>> localAddresses) {
        this.settings = settings;
        this.extraSubnets = extraSubnets == null ? List.of() : List.copyOf(extraSubnets);
        this.localAddresses = localAddresses;
    }

    public List<String> scan() {
        var subnets = subnets();
        var hosts = Ipv4Networks.hosts(subnets, settings.maxHosts());
        log.info("Ollama LAN scan: {} hosts across {} subnets on port {}",
                hosts.size(), subnets.size(), settings.port());
        return scanHosts(hosts, settings.port());
    }

    List<String> subnets() {
        var addresses = localAddresses.get();
        var localIp = addresses == null || addresses.isEmpty() ? null : addresses.get(0);
        return Ipv4Networks.localSubnets(localIp, extraSubnets);
    }

    List<String> scanHosts(List<String> hosts, int port) {
        var found = new ArrayList<String>();
        if (hosts == null || hosts.isEmpty()) return found;
        var permits = new Semaphore(MAX_CONCURRENT);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Boolean>>(hosts.size());
            for (var host : hosts) {
                futures.add(executor.submit(() -> connect(host, port, permits)));
            }
            for (var i = 0; i < hosts.size(); i++) {
                if (await(futures.get(i))) found.add(hosts.get(i));
            }
        }
        return found;
    }

    private boolean connect(String host, int port, Semaphore permits) {
        try {
            permits.acquire();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            return isOpen(host, port);
        } finally {
            permits.release();
        }
    }

    private boolean isOpen(String host, int port) {
        var timeout = Math.clamp(settings.connectTimeout().toMillis(), MIN_TIMEOUT_MS, MAX_TIMEOUT_MS);
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (IOException _) {
            return false;
        }
    }

    private static boolean await(Future<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.debug("Ollama host probe failed: {}", e.getMessage());
            return false;
        }
    }

    public static List<String> detectLocalAddresses() {
        var addresses = new ArrayList<String>();
        try {
            for (var networkInterface : NetworkInterface.networkInterfaces().toList()) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                for (var interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    var address = interfaceAddress.getAddress();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Could not enumerate network interfaces: {}", e.getMessage());
        }
        return addresses;
    }
}
