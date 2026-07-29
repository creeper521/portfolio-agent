package com.portfolio.agent.common.observability;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class AnonymousSourceHasher {
    private final byte[] secret;

    public AnonymousSourceHasher() {
        this.secret = new byte[32];
        new SecureRandom().nextBytes(this.secret);
    }

    public AnonymousSourceHasher(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("source hash secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    public String hash(String address) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    address.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot hash anonymous request source", exception);
        }
    }
}
