package com.portfolio.agent.turn.execution;

import java.util.List;
import java.util.Objects;

public final class TaskProvenance {
    private final List<String> publicSourceKeys;

    public TaskProvenance(List<String> publicSourceKeys) {
        this.publicSourceKeys = List.copyOf(Objects.requireNonNull(publicSourceKeys, "publicSourceKeys"));
    }

    public static TaskProvenance none() { return new TaskProvenance(List.of()); }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
}
