package com.portfolio.agent.turn.execution;

import java.util.List;
import java.util.Objects;

/**
 * 任务产出的公开溯源信息，不可变：仅保留已审核公开快照中的 Source 键列表，
 * 不携带任何私有知识库或未审核 Evidence 的标识。
 */
public final class TaskProvenance {
    private final List<String> publicSourceKeys;

    public TaskProvenance(List<String> publicSourceKeys) {
        this.publicSourceKeys = List.copyOf(Objects.requireNonNull(publicSourceKeys, "publicSourceKeys"));
    }

    public static TaskProvenance none() { return new TaskProvenance(List.of()); }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
}
