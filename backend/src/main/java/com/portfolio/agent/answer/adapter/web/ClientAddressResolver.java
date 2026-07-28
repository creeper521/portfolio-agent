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
        String candidate = forwarded.split(",", 2)[0].trim();
        return isLiteralAddress(candidate) ? candidate : remote;
    }

    private boolean isLiteralAddress(String value) {
        if (!value.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
