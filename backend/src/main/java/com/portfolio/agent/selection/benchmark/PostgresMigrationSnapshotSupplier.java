package com.portfolio.agent.selection.benchmark;

import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

@FunctionalInterface
public interface PostgresMigrationSnapshotSupplier {
    RuntimeContentSnapshot get();
}
