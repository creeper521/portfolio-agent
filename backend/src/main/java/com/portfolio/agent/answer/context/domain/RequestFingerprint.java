package com.portfolio.agent.answer.context.domain;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** Opaque normalized request fingerprint used only for idempotency. */
public final class RequestFingerprint {
    private final String value;
    public RequestFingerprint(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("fingerprint is required");
        this.value = value.trim();
    }
    public String value() { return value; }
    public static RequestFingerprint sha256Canonical(String canonicalValue) {
        if (canonicalValue == null || canonicalValue.isBlank()) {
            throw new IllegalArgumentException("canonical request is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue.trim().getBytes(StandardCharsets.UTF_8));
            return new RequestFingerprint(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
    @Override public boolean equals(Object other) { return other instanceof RequestFingerprint that && value.equals(that.value); }
    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return "RequestFingerprint{redacted=true}"; }
}
