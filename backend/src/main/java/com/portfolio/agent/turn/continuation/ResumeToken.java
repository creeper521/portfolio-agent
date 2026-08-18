package com.portfolio.agent.turn.continuation;

import java.util.Arrays;
import java.util.Base64;

/** Opaque 32-byte conversation credential. */
public final class ResumeToken {
    public static final int BYTE_LENGTH = 32;
    private final byte[] bytes;
    private ResumeToken(byte[] bytes) { this.bytes = bytes.clone(); }
    public static ResumeToken of(byte[] bytes) {
        if (bytes == null || bytes.length != BYTE_LENGTH) {
            throw new IllegalArgumentException("resume token must contain 32 bytes");
        }
        return new ResumeToken(bytes);
    }
    public static ResumeToken parse(String encoded) {
        try { return of(Base64.getUrlDecoder().decode(encoded)); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("resume token is invalid"); }
    }
    public String encode() { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public byte[] copyBytes() { return bytes.clone(); }
    @Override public boolean equals(Object other) {
        return other instanceof ResumeToken that && Arrays.equals(bytes, that.bytes);
    }
    @Override public int hashCode() { return Arrays.hashCode(bytes); }
    @Override public String toString() { return "ResumeToken{redacted=true}"; }
}
