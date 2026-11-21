package com.bankservices.bankservices_api.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Determines the client IP used as a rate-limit key.
 *
 * <p>By default the TCP peer address ({@code request.getRemoteAddr()}) is used and
 * {@code X-Forwarded-For} is ignored, because any client can send that header.
 *
 * <p>Only when the TCP peer is one of the configured trusted proxies
 * ({@code app.rate-limit.trusted-proxies}) is X-Forwarded-For read, from right to
 * left: trusted proxy hops are skipped and the first untrusted address is the
 * client. Entries a client may have prepended to the header are therefore never
 * reached, and malformed entries stop the walk (the last trusted hop is used).
 *
 * <p>Only literal IPv4/IPv6 addresses are accepted, so no DNS lookup can happen.
 */
public class ClientIpResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(List<String> trustedProxyRanges) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        if (trustedProxyRanges != null) {
            for (String range : trustedProxyRanges) {
                if (range != null && !range.isBlank()) {
                    // Throws at startup on an invalid entry: misconfiguration must be visible.
                    matchers.add(new IpAddressMatcher(range.trim()));
                }
            }
        }
        this.trustedProxies = List.copyOf(matchers);
    }

    public String resolve(HttpServletRequest request) {
        String peer = normalize(request.getRemoteAddr());

        if (peer == null) {
            return "unknown";
        }

        if (trustedProxies.isEmpty() || !isTrustedProxy(peer)) {
            return peer;
        }

        List<String> hops = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders(FORWARDED_FOR))) {
            for (String part : header.split(",")) {
                hops.add(part.trim());
            }
        }

        String lastTrusted = peer;
        for (int index = hops.size() - 1; index >= 0; index--) {
            String hop = normalize(hops.get(index));

            if (hop == null) {
                // Broken or forged chain: do not go further left.
                return lastTrusted;
            }

            if (!isTrustedProxy(hop)) {
                return hop;
            }

            lastTrusted = hop;
        }

        return lastTrusted;
    }

    boolean isTrustedProxy(String address) {
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    /** Canonical literal IP, or null when the value is not a literal IP address. */
    static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String candidate = value.trim();

        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        boolean ipv4 = IPV4.matcher(candidate).matches();
        boolean ipv6 = !ipv4 && candidate.indexOf(':') >= 0 && IPV6.matcher(candidate).matches();

        // An out-of-range dotted value (e.g. 999.1.1.1) is NOT a literal for
        // InetAddress and would be resolved as a host name: reject it here.
        if (!(ipv4 && validOctets(candidate)) && !ipv6) {
            return null;
        }

        try {
            // A literal is parsed locally; no name resolution takes place.
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean validOctets(String ipv4) {
        for (String octet : ipv4.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }
}
