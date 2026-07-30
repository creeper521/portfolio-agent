package com.portfolio.agent.portfolio.repository.postgres;

import java.util.Objects;

public final class EncodedRuntimeSnapshot {

    private final String payload;
    private final String checksum;

    public EncodedRuntimeSnapshot(String payload, String checksum) {
        this.payload = Objects.requireNonNull(payload, "payload");
        this.checksum = Objects.requireNonNull(checksum, "checksum");
    }

    public String getPayload() {
        return payload;
    }

    public String getChecksum() {
        return checksum;
    }
}
