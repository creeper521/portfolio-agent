package com.portfolio.agent.answer.adapter.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.Set;

public final class ClientAddressResolver {
    private final boolean trustProxy;
    private final Set<String> trustedProxies;

    public ClientAddressResolver(boolean trustProxy, Set<String> trustedProxies) {
        this.trustProxy = trustProxy;
        this.trustedProxies = Set.copyOf(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustProxy || !trustedProxies.contains(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        for (String hop : hops) {
            if (!isLiteralAddress(hop.trim())) {
                return remote;
            }
        }
        for (int index = hops.length - 1; index >= 0; index--) {
            String candidate = hops[index].trim();
            if (!trustedProxies.contains(candidate)) {
                return candidate;
            }
        }
        return remote;
    }

    private boolean isLiteralAddress(String value) {
        if (value.contains(":")) {
            if (!value.matches("[0-9a-fA-F:]+")) {
                return false;
            }
        } else {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) {
                return false;
            }
            for (String part : parts) {
                if (!part.matches("\\d{1,3}") || Integer.parseInt(part) > 255) {
                    return false;
                }
            }
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
