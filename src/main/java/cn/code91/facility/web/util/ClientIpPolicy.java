package cn.code91.facility.web.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Nullable;
import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

/** Resolves numeric network origin using only explicitly trusted proxy CIDRs; never performs DNS. */
public class ClientIpPolicy {
    private final List<Network> trusted;

    public ClientIpPolicy(List<String> trustedProxies) {
        Objects.requireNonNull(trustedProxies, "trustedProxies");
        if (trustedProxies.size() > 128) throw new IllegalArgumentException("At most 128 trusted proxy CIDRs");
        trusted = trustedProxies.stream().map(Network::parse).toList();
    }

    /** Missing or invalid numeric peers have the stable value {@code unknown}. */
    public String resolve(@Nullable HttpServletRequest request) {
        if (request == null) return "unknown";
        InetAddress peer = literal(request.getRemoteAddr());
        if (peer == null) return "unknown";
        var headers = request.getHeaders("X-Forwarded-For");
        if (!isTrusted(peer) || Boolean.TRUE.equals(request.getAttribute("org.apache.tomcat.request.forwarded"))
                || headers == null || !headers.hasMoreElements()) return peer.getHostAddress();
        String header = headers.nextElement();
        if (headers.hasMoreElements() || header == null || header.length() > 2048) return peer.getHostAddress();
        if (header.chars().anyMatch(c -> c < 32 || c > 126)) return peer.getHostAddress();
        if (header.isBlank()) return peer.getHostAddress();
        String[] hops = header.split(",", -1);
        if (hops.length > 32) return peer.getHostAddress();
        InetAddress[] parsed = new InetAddress[hops.length];
        for (int i = 0; i < hops.length; i++) {
            parsed[i] = literal(hops[i].trim());
            if (parsed[i] == null) return peer.getHostAddress();
        }
        InetAddress current = peer;
        for (int i = hops.length - 1; i >= 0 && isTrusted(current); i--) {
            current = parsed[i];
        }
        return current.getHostAddress();
    }

    private boolean isTrusted(InetAddress address) {
        return trusted.stream().anyMatch(network -> network.contains(address.getAddress()));
    }

    private static @Nullable InetAddress literal(@Nullable String value) {
        if (value == null || value.isEmpty() || value.length() > 45 || !value.matches("[0-9A-Fa-f:.]+")) return null;
        if (value.indexOf('.') >= 0) {
            String quad = value.substring(value.lastIndexOf(':') + 1);
            if (!quad.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) return null;
        } else if (value.indexOf(':') < 0) return null;
        try { return InetAddress.ofLiteral(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private record Network(byte[] bytes, int prefix) {
        static Network parse(String cidr) {
            Objects.requireNonNull(cidr, "trusted proxy CIDR");
            if (cidr.length() > 49) throw new IllegalArgumentException("Invalid trusted proxy CIDR");
            String[] parts = cidr.split("/", -1);
            if (parts.length == 2 && !parts[1].matches("[0-9]{1,3}")) throw new IllegalArgumentException("Invalid trusted proxy prefix");
            InetAddress address = literal(parts[0]);
            if (address == null || parts.length > 2) throw new IllegalArgumentException("Invalid trusted proxy CIDR");
            int bits = address.getAddress().length * 8;
            int prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Invalid trusted proxy prefix");
            return new Network(address.getAddress(), prefix);
        }
        boolean contains(byte[] candidate) {
            if (candidate.length != bytes.length) return false;
            for (int bit = 0; bit < prefix; bit++) {
                int mask = 1 << (7 - bit % 8);
                if ((candidate[bit / 8] & mask) != (bytes[bit / 8] & mask)) return false;
            }
            return true;
        }
    }
}
