package com.portfolio.agent.release.benchmark.selection;

import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

@FunctionalInterface
public interface PostgresMigrationSnapshotSupplier {
    RuntimeContentSnapshot get();
}
