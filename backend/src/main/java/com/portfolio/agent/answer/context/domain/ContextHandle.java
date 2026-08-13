package com.portfolio.agent.answer.context.domain;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Opaque 24-byte context identifier. */
public final class ContextHandle {
    public static final int BYTE_LENGTH = 24;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] bytes;
    private ContextHandle(byte[] bytes) { this.bytes = bytes.clone(); }
    public static ContextHandle issue() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return new ContextHandle(bytes);
    }
    public static ContextHandle fromBase64Url(String value) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (bytes.length != BYTE_LENGTH) throw new IllegalArgumentException("invalid context handle");
            return new ContextHandle(bytes);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid context handle", exception);
        }
    }
    public String asBase64Url() { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    @Override public boolean equals(Object other) { return other instanceof ContextHandle that && Arrays.equals(bytes, that.bytes); }
    @Override public int hashCode() { return Arrays.hashCode(bytes); }
    @Override public String toString() { return "ContextHandle{redacted=true}"; }
}
