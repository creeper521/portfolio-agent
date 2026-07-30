package com.portfolio.agent.selection.benchmark;

import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.postgres.PostgresPublicPortfolioRepository;
import java.util.Objects;

public final class PostgresRepositoryMigrationSnapshotSupplier
        implements PostgresMigrationSnapshotSupplier {
    private final PostgresPublicPortfolioRepository repository;

    public PostgresRepositoryMigrationSnapshotSupplier(
            PostgresPublicPortfolioRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public RuntimeContentSnapshot get() {
        return repository.getSnapshot();
    }
}
