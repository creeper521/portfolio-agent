package com.portfolio.agent.portfolio.repository.postgres;

import java.util.Objects;

final class StoredRuntimeSnapshot {

    private final String payload;
    private final String checksum;

    StoredRuntimeSnapshot(String payload, String checksum) {
        this.payload = Objects.requireNonNull(payload, "payload");
        this.checksum = checksum;
    }

    String getPayload() {
        return payload;
    }

    String getChecksum() {
        return checksum;
    }
}
