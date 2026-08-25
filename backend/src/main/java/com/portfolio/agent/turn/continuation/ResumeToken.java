package com.portfolio.agent.turn.continuation;

import java.util.Arrays;
import java.util.Base64;

/** Opaque 32-byte conversation credential. */
public final class ResumeToken {
    public static final int BYTE_LENGTH = 32;
    private final byte[] bytes;
    private ResumeToken(byte[] bytes) { this.bytes = bytes.clone(); }
    /** 从原始字节构造令牌。
     *
     * @throws IllegalArgumentException 字节为 null 或长度不等于 32
     */
    public static ResumeToken of(byte[] bytes) {
        if (bytes == null || bytes.length != BYTE_LENGTH) {
            throw new IllegalArgumentException("resume token must contain 32 bytes");
        }
        return new ResumeToken(bytes);
    }
    /** 解析 Base64url 编码的令牌；任何解码或长度失败统一转为 IllegalArgumentException。 */
    public static ResumeToken parse(String encoded) {
        try { return of(Base64.getUrlDecoder().decode(encoded)); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("resume token is invalid"); }
    }
    /** 编码为无填充 Base64url 字符串，供客户端持有回传。 */
    public String encode() { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    /** 返回内部字节的防御性副本。 */
    public byte[] copyBytes() { return bytes.clone(); }
    @Override public boolean equals(Object other) {
        return other instanceof ResumeToken that && Arrays.equals(bytes, that.bytes);
    }
    @Override public int hashCode() { return Arrays.hashCode(bytes); }
    @Override public String toString() { return "ResumeToken{redacted=true}"; }
}
