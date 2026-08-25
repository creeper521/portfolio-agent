package com.portfolio.agent.turn.state.postgres;

import org.springframework.beans.factory.InitializingBean;

import java.time.Clock;

/** PostgreSQL Agent State 对外可用前的未过期密钥覆盖硬门。 */
public final class AgentStateKeyCoverageReadiness implements InitializingBean {
    private final JdbcAgentStateStore store;
    private final Clock clock;

    public AgentStateKeyCoverageReadiness(JdbcAgentStateStore store, Clock clock) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    /** 启动门槛：初始化即断言未过期数据不依赖任何缺失密钥，失败则拒绝启动。 */
    @Override public void afterPropertiesSet() {
        store.assertKeyCoverage(clock.instant());
    }
}
