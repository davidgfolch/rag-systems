package com.rag.provider.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

/**
 * Pure IPv4 helpers for LAN discovery: derives the candidate subnets from a
 * local address and expands CIDRs into scan targets. No I/O happens here.
 */
public final class Ipv4Networks {

    private static final int OCTETS = 4;
    private static final int BITS_PER_OCTET = 8;
    private static final int IPV4_BITS = 32;
    private static final int MAX_OCTET = 255;
    private static final int PREFIX_24 = 24;
    private static final int PREFIX_30 = 30;
    private static final int ADJACENT = 1;
    private static final int ABSOLUTE_MAX_HOSTS = 65_536;
    private static final String OCTET_SEPARATOR = "\\.";
    private static final String CIDR_SEPARATOR = "/";
    private static final String SUBNET_FORMAT = "%s.%s.%s.0/%d";
    private static final String IP_FORMAT = "%d.%d.%d.%d";
    @SuppressWarnings("java:S1313")
    private static final List<String> COMMON_LAN_SUBNETS = List.of("192.168.1.0/24", "192.168.0.0/24");

    private Ipv4Networks() {}

    /** Local /24, adjacent /24s, common home LANs and the configured extras, deduplicated. */
    public static List<String> localSubnets(String localIp, List<String> extra) {
        var subnets = new LinkedHashSet<String>();
        if (localIp != null && !localIp.isBlank()) {
            addLocalSubnets(subnets, localIp);
        }
        subnets.addAll(COMMON_LAN_SUBNETS);
        if (extra != null) {
            subnets.addAll(extra);
        }
        return List.copyOf(subnets);
    }

    /** Expands every CIDR into hosts, stopping at {@code maxHosts} (0 means the absolute safety cap). */
    public static List<String> hosts(List<String> cidrs, int maxHosts) {
        var hosts = new ArrayList<String>();
        if (cidrs == null) return hosts;
        var budget = maxHosts > 0 ? maxHosts : ABSOLUTE_MAX_HOSTS;
        for (var cidr : cidrs) {
            if (hosts.size() >= budget) break;
            hosts.addAll(hosts(cidr, budget - hosts.size()));
        }
        return hosts;
    }

    /** Expands one CIDR into its usable host addresses. Invalid CIDRs yield an empty list. */
    public static List<String> hosts(String cidr, int maxHosts) {
        var parts = cidr == null ? new String[0] : cidr.strip().split(CIDR_SEPARATOR);
        if (parts.length != 2) return List.of();
        long base;
        int prefix;
        try {
            base = toLong(parts[0]);
            prefix = Integer.parseInt(parts[1]);
        } catch (RuntimeException _) {
            return List.of();
        }
        if (prefix < 0 || prefix > IPV4_BITS) return List.of();
        return getHosts(maxHosts, prefix, base);
    }

    private static List<String> getHosts(int maxHosts, int prefix, long base) {
        var mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (IPV4_BITS - prefix)) & 0xFFFFFFFFL;
        var network = base & mask;
        var broadcast = network | ~mask & 0xFFFFFFFFL;
        var first = prefix <= PREFIX_30 ? network + ADJACENT : network;
        var last = prefix <= PREFIX_30 ? broadcast - ADJACENT : broadcast;
        var limit = maxHosts > 0 ? Math.min(last, first + maxHosts - 1) : last;
        return LongStream.rangeClosed(first, limit).mapToObj(Ipv4Networks::fromLong).toList();
    }

    private static void addLocalSubnets(Set<String> subnets, String localIp) {
        long value;
        try {
            value = toLong(localIp);
        } catch (RuntimeException _) {
            return;
        }
        var parts = localIp.strip().split(OCTET_SEPARATOR);
        subnets.add(String.format(SUBNET_FORMAT, parts[0], parts[1], parts[2], PREFIX_24));
        var third = (int) (value >>> BITS_PER_OCTET & MAX_OCTET);
        addAdjacent(subnets, parts, third, -ADJACENT);
        addAdjacent(subnets, parts, third, ADJACENT);
    }

    private static void addAdjacent(Set<String> subnets, String[] parts, int third, int offset) {
        var adjacent = third + offset;
        if (adjacent >= 0 && adjacent <= MAX_OCTET) {
            subnets.add(String.format(SUBNET_FORMAT, parts[0], parts[1], adjacent, PREFIX_24));
        }
    }

    private static long toLong(String ip) {
        var parts = ip.strip().split(OCTET_SEPARATOR);
        if (parts.length != OCTETS) throw new IllegalArgumentException("Not an IPv4 address: " + ip);
        long value = 0;
        for (var part : parts) {
            var octet = Integer.parseInt(part);
            if (octet < 0 || octet > MAX_OCTET) throw new IllegalArgumentException("Invalid octet: " + part);
            value = (value << BITS_PER_OCTET) | octet;
        }
        return value;
    }

    private static String fromLong(long ip) {
        return String.format(IP_FORMAT,
                ip >>> (BITS_PER_OCTET * 3) & MAX_OCTET,
                ip >>> (BITS_PER_OCTET * 2) & MAX_OCTET,
                ip >>> BITS_PER_OCTET & MAX_OCTET,
                ip & MAX_OCTET);
    }
}
