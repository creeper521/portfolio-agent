package com.portfolio.agent.answer.context.domain;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Opaque 32-byte resume credential. Its value is never rendered in diagnostics. */
public final class ResumeToken {
    public static final int BYTE_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] bytes;

    private ResumeToken(byte[] bytes) { this.bytes = bytes.clone(); }
    public static ResumeToken issue() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return new ResumeToken(bytes);
    }
    public static ResumeToken fromBase64Url(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(requireText(encoded));
            if (decoded.length != BYTE_LENGTH) throw new IllegalArgumentException("invalid resume token");
            return new ResumeToken(decoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid resume token", exception);
        }
    }
    public String asBase64Url() { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public byte[] copyBytes() { return bytes.clone(); }
    @Override public boolean equals(Object other) { return other instanceof ResumeToken that && Arrays.equals(bytes, that.bytes); }
    @Override public int hashCode() { return Arrays.hashCode(bytes); }
    @Override public String toString() { return "ResumeToken{redacted=true}"; }
    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("resume token is required");
        return value.trim();
    }
}
