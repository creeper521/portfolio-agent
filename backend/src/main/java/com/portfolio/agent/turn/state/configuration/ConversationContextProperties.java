package com.portfolio.agent.turn.state.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 会话上下文（Agent State）模式与留存配置（portfolio.conversation-context 前缀）。
 *
 * <p>三种模式：DISABLED（只读作品集）、IN_MEMORY（快速测试）、POSTGRESQL（标准
 * 生产）。TTL 体系——绝对/空闲留存、澄清 TTL（更短）、讨论 TTL（≤30 分钟）与
 * 密钥轮换保留期，关系约束在 {@link #validate()} 中 fail-closed 校验。</p>
 */
@ConfigurationProperties(prefix = "portfolio.conversation-context")
public class ConversationContextProperties {
    private Mode mode = Mode.DISABLED;
    private Duration idleTtl = Duration.ofMinutes(30);
    private Duration absoluteTtl = Duration.ofMinutes(30);
    private Duration clarificationTtl = Duration.ofMinutes(5);
    private Duration discussionTtl = Duration.ofMinutes(20);
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
    public Duration getDiscussionTtl() { return discussionTtl; }
    public void setDiscussionTtl(Duration value) { discussionTtl = value; }
    public Duration getKeyRotationRetention() { return keyRotationRetention; }
    public void setKeyRotationRetention(Duration value) { keyRotationRetention = value; }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    public int getCleanupBatchSize() { return cleanupBatchSize; }
    public void setCleanupBatchSize(int cleanupBatchSize) { this.cleanupBatchSize = cleanupBatchSize; }
    public Crypto getCrypto() { return crypto; }

    /**
     * fail-closed 校验留存与密钥配置：空闲 TTL 必须等于绝对 TTL；澄清/讨论 TTL
     * 为正且不超过绝对 TTL（讨论另受 30 分钟硬上限约束）；密钥轮换保留期必须
     * 覆盖绝对 TTL + 清理间隔；POSTGRESQL 模式下令牌密钥与载荷密钥必须分离，
     * 且 current/previous 密钥 id 不得重复。
     *
     * @throws IllegalStateException 任一约束被违反
     */
    public void validate() {
        if (idleTtl.isNegative() || idleTtl.isZero()
                || absoluteTtl.isNegative() || absoluteTtl.isZero()
                || !idleTtl.equals(absoluteTtl)
                || clarificationTtl.isNegative() || clarificationTtl.isZero()
                || clarificationTtl.compareTo(absoluteTtl) > 0
                || discussionTtl.isNegative() || discussionTtl.isZero()
                || discussionTtl.compareTo(Duration.ofMinutes(30)) > 0
                || discussionTtl.compareTo(absoluteTtl) >= 0
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

    /** 拒绝同一密钥世代复用 id（current 与 previous 相同会使轮换判定失效）。 */
    private void rejectReusedId(String current, String previous, String name) {
        if (current != null && previous != null
                && !current.isBlank() && current.trim().equals(previous.trim())) {
            throw new IllegalStateException(name + " current and previous key ids must differ");
        }
    }

    /** 令牌与载荷两族密钥的轮换配置：current 必填于使用处，previous 必须成对出现。 */
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

        /** previous 密钥的 id 与值必须同时出现或同时缺失。 */
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
    /** State 模式：DISABLED 只读 / IN_MEMORY 快速测试 / POSTGRESQL 标准生产。 */
    public enum Mode { DISABLED, IN_MEMORY, POSTGRESQL }
}
