package com.aquafish.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpAddressRuleTest {

    @Test
    void shouldMatchIpv4AddressAndCidr() {
        IpAddressRule exact = IpAddressRule.parse("192.168.10.8");
        IpAddressRule network = IpAddressRule.parse("192.168.10.0/24");

        assertEquals(4, exact.version());
        assertTrue(exact.matches("192.168.10.8"));
        assertFalse(exact.matches("192.168.10.9"));
        assertTrue(network.matches("192.168.10.199"));
        assertFalse(network.matches("192.168.11.1"));
    }

    @Test
    void shouldMatchIpv6CidrWithoutMixingAddressVersions() {
        IpAddressRule network = IpAddressRule.parse("2001:db8::/32");

        assertEquals(6, network.version());
        assertTrue(network.matches("2001:db8:1::8"));
        assertFalse(network.matches("2001:db9::1"));
        assertFalse(network.matches("127.0.0.1"));
    }

    @Test
    void shouldRejectHostNamesAndInvalidPrefix() {
        assertThrows(
            IllegalArgumentException.class,
            () -> IpAddressRule.parse("example.com")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> IpAddressRule.parse("10.0.0.0/33")
        );
    }
}
