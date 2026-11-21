package com.bankservices.bankservices_api.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String peer, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        for (String value : forwardedFor) {
            request.addHeader(ClientIpResolver.FORWARDED_FOR, value);
        }
        return request;
    }

    @Test
    void withoutTrustedProxies_forwardedForIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());

        assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
        assertThat(resolver.resolve(request("198.51.100.7", "9.9.9.9", "8.8.8.8"))).isEqualTo("198.51.100.7");
    }

    @Test
    void untrustedPeer_cannotSpoofItsAddress_evenWhenProxiesAreConfigured() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    void trustedProxy_rightmostUntrustedAddressIsTheClient() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));

        // The client prepended a fake "6.6.6.6"; the proxy appended the real peer 203.0.113.9.
        assertThat(resolver.resolve(request("10.0.0.1", "6.6.6.6, 203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void chainOfTrustedProxies_isSkipped_acrossSeveralHeaders() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8", "192.168.1.5"));

        assertThat(resolver.resolve(request("10.0.0.1", "203.0.113.9, 192.168.1.5", "10.0.0.2")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void malformedHop_stopsTheWalk_atTheLastTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve(request("10.0.0.1", "203.0.113.9, not-an-ip"))).isEqualTo("10.0.0.1");
        assertThat(resolver.resolve(request("10.0.0.1"))).isEqualTo("10.0.0.1");
    }

    @Test
    void onlyLiteralAddresses_areAccepted_soNoDnsLookupCanHappen() {
        assertThat(ClientIpResolver.normalize("example.com")).isNull();
        assertThat(ClientIpResolver.normalize("999.1.1.1")).isNull();      // not a literal: would be a DNS name
        assertThat(ClientIpResolver.normalize("1.2.3.4; drop")).isNull();
        assertThat(ClientIpResolver.normalize("")).isNull();
        assertThat(ClientIpResolver.normalize(null)).isNull();
    }

    @Test
    void addressesAreNormalized() {
        assertThat(ClientIpResolver.normalize(" 203.0.113.9 ")).isEqualTo("203.0.113.9");
        assertThat(ClientIpResolver.normalize("[2001:db8::1]")).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(ClientIpResolver.normalize("::ffff:203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void invalidTrustedProxyConfiguration_failsFast() {
        assertThatThrownBy(() -> new ClientIpResolver(List.of("not-a-cidr")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
