package com.portfolio.agent.turn.api;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单个匿名来源请求的并发租约。
 *
 * <p>租约只负责释放来源级并发计数；限流窗口不会因关闭租约而回滚。</p>
 */
public final class AgentRequestAdmission implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable release;

    AgentRequestAdmission(Runnable release) {
        this.release = Objects.requireNonNull(release, "release must not be null");
    }

    /** 幂等释放：只执行一次来源并发计数回调，重复 close 是空操作。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release.run();
        }
    }
}
