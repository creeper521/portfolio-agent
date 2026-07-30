package com.portfolio.agent.ingestion.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.ingestion.domain.ImportedMarkdownChunk;
import com.portfolio.agent.ingestion.domain.ImportedMarkdownDocument;
import com.portfolio.agent.ingestion.domain.MarkdownRevisionStatus;
import com.portfolio.agent.ingestion.domain.MarkdownVectorStatus;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSourceDocumentRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private JdbcTemplate jdbcTemplate;
    private PostgresSourceDocumentRepository repository;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/governance")
                .table("flyway_schema_history_governance")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/governance")
                .table("flyway_schema_history_governance")
                .load()
                .migrate();
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new PostgresSourceDocumentRepository(jdbcTemplate);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void migratesAndPreservesCurrentRevisionAcrossPendingRollbackAndMissingLifecycle() {
        ImportedMarkdownDocument first = document("note.md", hash(1), MarkdownRevisionStatus.PARSED);
        repository.saveRevision(first);
        String firstRevision = jdbcTemplate.queryForObject("""
                SELECT current_revision_id::text FROM source_document WHERE relative_path = 'note.md'
                """, String.class);

        transactions.executeWithoutResult(status -> {
            repository.saveRevision(document("note.md", hash(2), MarkdownRevisionStatus.VECTOR_PENDING));
            status.setRollbackOnly();
        });
        repository.saveRevision(document("note.md", hash(3), MarkdownRevisionStatus.VECTOR_PENDING));
        repository.markMissing("note.md");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT current_revision_id::text FROM source_document WHERE relative_path = 'note.md'
                """, String.class)).isEqualTo(firstRevision);
        assertThat(jdbcTemplate.queryForObject("SELECT lifecycle_status FROM source_document WHERE relative_path = 'note.md'", String.class))
                .isEqualTo("MISSING");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_revision", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_chunk WHERE embedding IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void acceptsUuidVectorAndJsonbValuesFromGovernanceSchema() {
        repository.saveRevision(document("note.md", hash(9), MarkdownRevisionStatus.PARSED));
        String revisionId = jdbcTemplate.queryForObject("SELECT revision_id::text FROM source_revision", String.class);

        jdbcTemplate.update("""
                INSERT INTO source_link_suggestion
                    (suggestion_id, revision_id, target_kind, suggestion_payload, review_status)
                VALUES (CAST(? AS uuid), CAST(? AS uuid), 'CLAIM', CAST(? AS jsonb), 'PENDING')
                """, java.util.UUID.randomUUID().toString(), revisionId, "{\"source\":\"integration\"}");

        assertThat(jdbcTemplate.queryForObject("SELECT embedding::text FROM source_chunk", String.class))
                .startsWith("[").endsWith("]");
        assertThat(jdbcTemplate.queryForObject("SELECT suggestion_payload->>'source' FROM source_link_suggestion", String.class))
                .isEqualTo("integration");
    }

    private ImportedMarkdownDocument document(String path, String hash, MarkdownRevisionStatus status) {
        float[] vector = new float[512];
        vector[0] = 1.0f;
        ImportedMarkdownChunk chunk = new ImportedMarkdownChunk(
                0, hash, "private", status == MarkdownRevisionStatus.PARSED ? vector : null,
                status == MarkdownRevisionStatus.PARSED ? MarkdownVectorStatus.READY : MarkdownVectorStatus.VECTOR_PENDING);
        return new ImportedMarkdownDocument(path, hash, 7, List.of(chunk), status);
    }

    private String hash(int value) {
        return String.format("%064d", value);
    }
}
