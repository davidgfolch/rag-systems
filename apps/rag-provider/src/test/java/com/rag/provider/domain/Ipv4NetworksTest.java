package com.rag.provider.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Ipv4NetworksTest {

    @Test
    void shouldDeriveLocalSubnetAndAdjacentSubnets() {
        var subnets = Ipv4Networks.localSubnets("192.168.5.10", List.of());
        assertThat(subnets)
                .containsSubsequence("192.168.5.0/24", "192.168.4.0/24", "192.168.6.0/24")
                .contains("192.168.1.0/24", "192.168.0.0/24");
    }

    @Test
    void shouldNotWrapAdjacentSubnetOctet() {
        assertThat(Ipv4Networks.localSubnets("192.168.0.5", List.of())).doesNotContain("192.168.-1.0/24");
        assertThat(Ipv4Networks.localSubnets("192.168.255.5", List.of())).doesNotContain("192.168.256.0/24");
    }

    @Test
    void shouldDeduplicateCommonAndConfiguredSubnets() {
        var subnets = Ipv4Networks.localSubnets("192.168.1.10", List.of("192.168.1.0/24", "10.0.0.0/24"));
        assertThat(subnets.stream().filter("192.168.1.0/24"::equals)).hasSize(1);
        assertThat(subnets).contains("10.0.0.0/24");
    }

    @Test
    void shouldFallBackToCommonSubnetsWhenLocalIpIsNotUsable() {
        assertThat(Ipv4Networks.localSubnets(null, null))
                .containsExactly("192.168.1.0/24", "192.168.0.0/24");
        assertThat(Ipv4Networks.localSubnets("not-an-ip", null))
                .containsExactly("192.168.1.0/24", "192.168.0.0/24");
        assertThat(Ipv4Networks.localSubnets("10.0.0", null))
                .containsExactly("192.168.1.0/24", "192.168.0.0/24");
        assertThat(Ipv4Networks.localSubnets("10.0.x.1", null))
                .containsExactly("192.168.1.0/24", "192.168.0.0/24");
    }

    @Test
    void shouldExpandCidrIntoUsableHosts() {
        assertThat(Ipv4Networks.hosts("192.168.1.0/24", 3))
                .containsExactly("192.168.1.1", "192.168.1.2", "192.168.1.3");
    }

    @Test
    void shouldKeepEverythingForSmallPrefixes() {
        assertThat(Ipv4Networks.hosts("10.0.0.0/30", 0)).containsExactly("10.0.0.1", "10.0.0.2");
        assertThat(Ipv4Networks.hosts("10.0.0.0/31", 0)).containsExactly("10.0.0.0", "10.0.0.1");
        assertThat(Ipv4Networks.hosts("10.0.0.5/32", 0)).containsExactly("10.0.0.5");
    }

    @Test
    void shouldCapTheTotalHostBudgetAcrossCidrs() {
        assertThat(Ipv4Networks.hosts(List.of("10.0.0.0/30", "10.0.0.4/30"), 3))
                .containsExactly("10.0.0.1", "10.0.0.2", "10.0.0.5");
    }

    @Test
    void shouldTreatZeroMaxHostsAsTheSafetyCap() {
        assertThat(Ipv4Networks.hosts("0.0.0.0/0", 2)).containsExactly("0.0.0.1", "0.0.0.2");
        assertThat(Ipv4Networks.hosts(List.of(), 0)).isEmpty();
        assertThat(Ipv4Networks.hosts((List<String>) null, 0)).isEmpty();
    }

    @ParameterizedTest(name = "should reject invalid cidr \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"nope", "10.0.0.0", "10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/abc",
            "10.0.999.0/24", "10.0.0/24", "-1.0.0.0/24", "10.0.0.0/24/8"})
    void shouldReturnNoHostsForInvalidCidr(String cidr) {
        assertThat(Ipv4Networks.hosts(cidr, 5)).isEmpty();
    }
}
