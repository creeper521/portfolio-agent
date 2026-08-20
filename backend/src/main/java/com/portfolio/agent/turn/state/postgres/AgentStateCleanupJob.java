package com.portfolio.agent.turn.state.postgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** 定期触发有界清理；日志只包含固定类别计数，不包含任何会话或密钥标识。 */
public final class AgentStateCleanupJob {
    private static final Logger LOG = LoggerFactory.getLogger(AgentStateCleanupJob.class);
    private final JdbcAgentStateStore store;

    public AgentStateCleanupJob(JdbcAgentStateStore store) {
        this.store = java.util.Objects.requireNonNull(store, "store");
    }

    @Scheduled(fixedDelayString = "${portfolio.conversation-context.cleanup-interval:15m}")
    public void cleanup() {
        JdbcAgentStateStore.CleanupResult result = store.cleanup();
        if (result.total() > 0) {
            LOG.info("Agent State cleanup completed: expiredExecutions={}, expiredContexts={}, "
                            + "expiredChallenges={}, expiredSessions={}, revokedSessions={}, "
                            + "orphanRows={}, unsupportedKeys={}",
                    result.expiredExecutions(), result.expiredContexts(),
                    result.expiredChallenges(), result.expiredSessions(),
                    result.revokedSessions(), result.orphanRows(), result.unsupportedKeys());
        }
    }
}
