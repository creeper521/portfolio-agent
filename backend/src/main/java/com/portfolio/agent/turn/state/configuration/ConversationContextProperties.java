package com.portfolio.agent.turn.state.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "portfolio.conversation-context")
public class ConversationContextProperties {
    private Mode mode = Mode.DISABLED;
    private Duration idleTtl = Duration.ofMinutes(30);
    private Duration absoluteTtl = Duration.ofMinutes(30);
    private Duration clarificationTtl = Duration.ofMinutes(5);
    private Duration keyRotationRetention = Duration.ofMinutes(45);
    private Duration cleanupInterval = Duration.ofMinutes(15);
    private int cleanupBatchSize = 500;
    private final Crypto crypto = new Crypto();

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public Duration getIdleTtl() { return idleTtl; }
    public void setIdleTtl(Duration idleTtl) { this.idleTtl = idleTtl; }
    public Duration getAbsoluteTtl() { return absoluteTtl; }
    public void setAbsoluteTtl(Duration absoluteTtl) { this.absoluteTtl = absoluteTtl; }
    public Duration getClarificationTtl() { return clarificationTtl; }
    public void setClarificationTtl(Duration value) { clarificationTtl = value; }
    public Duration getKeyRotationRetention() { return keyRotationRetention; }
    public void setKeyRotationRetention(Duration value) { keyRotationRetention = value; }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    public int getCleanupBatchSize() { return cleanupBatchSize; }
    public void setCleanupBatchSize(int cleanupBatchSize) { this.cleanupBatchSize = cleanupBatchSize; }
    public Crypto getCrypto() { return crypto; }

    public void validate() {
        if (idleTtl.isNegative() || idleTtl.isZero()
                || absoluteTtl.isNegative() || absoluteTtl.isZero()
                || !idleTtl.equals(absoluteTtl)
                || clarificationTtl.isNegative() || clarificationTtl.isZero()
                || clarificationTtl.compareTo(absoluteTtl) > 0
                || keyRotationRetention.compareTo(absoluteTtl.plus(cleanupInterval)) < 0
                || cleanupInterval.isNegative() || cleanupInterval.isZero()
                || cleanupBatchSize < 1 || cleanupBatchSize > 500) {
            throw new IllegalStateException("invalid conversation Context retention settings");
        }
        crypto.validatePairs();
        if (mode == Mode.POSTGRESQL
                && crypto.currentTokenKeyId != null && crypto.currentPayloadKeyId != null
                && crypto.currentTokenKeyId.trim().equals(crypto.currentPayloadKeyId.trim())) {
            throw new IllegalStateException("token and payload key ids must be different");
        }
        rejectReusedId(crypto.currentTokenKeyId, crypto.previousTokenKeyId, "token");
        rejectReusedId(crypto.currentPayloadKeyId, crypto.previousPayloadKeyId, "payload");
    }

    private void rejectReusedId(String current, String previous, String name) {
        if (current != null && previous != null
                && !current.isBlank() && current.trim().equals(previous.trim())) {
            throw new IllegalStateException(name + " current and previous key ids must differ");
        }
    }

    public static class Crypto {
        private String currentTokenKeyId;
        private String currentTokenKey;
        private String previousTokenKeyId;
        private String previousTokenKey;
        private String currentPayloadKeyId;
        private String currentPayloadKey;
        private String previousPayloadKeyId;
        private String previousPayloadKey;

        public String getCurrentTokenKeyId() { return currentTokenKeyId; }
        public void setCurrentTokenKeyId(String value) { currentTokenKeyId = value; }
        public String getCurrentTokenKey() { return currentTokenKey; }
        public void setCurrentTokenKey(String value) { currentTokenKey = value; }
        public String getPreviousTokenKeyId() { return previousTokenKeyId; }
        public void setPreviousTokenKeyId(String value) { previousTokenKeyId = value; }
        public String getPreviousTokenKey() { return previousTokenKey; }
        public void setPreviousTokenKey(String value) { previousTokenKey = value; }
        public String getCurrentPayloadKeyId() { return currentPayloadKeyId; }
        public void setCurrentPayloadKeyId(String value) { currentPayloadKeyId = value; }
        public String getCurrentPayloadKey() { return currentPayloadKey; }
        public void setCurrentPayloadKey(String value) { currentPayloadKey = value; }
        public String getPreviousPayloadKeyId() { return previousPayloadKeyId; }
        public void setPreviousPayloadKeyId(String value) { previousPayloadKeyId = value; }
        public String getPreviousPayloadKey() { return previousPayloadKey; }
        public void setPreviousPayloadKey(String value) { previousPayloadKey = value; }

        private void validatePairs() {
            requirePair(previousTokenKeyId, previousTokenKey, "previous token key");
            requirePair(previousPayloadKeyId, previousPayloadKey, "previous payload key");
        }
        private void requirePair(String id, String key, String name) {
            if ((id == null || id.isBlank()) != (key == null || key.isBlank())) {
                throw new IllegalStateException(name + " id and value must be configured together");
            }
        }
    }
    public enum Mode { DISABLED, IN_MEMORY, POSTGRESQL }
}
