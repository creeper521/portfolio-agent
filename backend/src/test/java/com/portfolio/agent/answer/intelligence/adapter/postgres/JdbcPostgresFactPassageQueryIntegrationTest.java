package com.portfolio.agent.answer.intelligence.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleDatabaseImporter;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleImportResult;
import com.portfolio.agent.portfolio.repository.postgres.PublicRuntimeSnapshotCodec;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPostgresFactPassageQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");

    private JdbcTemplate jdbcTemplate;
    private String releaseId;

    @BeforeEach
    void setUp() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/public")
                .table("flyway_schema_history_public")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        PublicRuntimeSnapshotCodec codec = new PublicRuntimeSnapshotCodec(
                new ObjectMapper().findAndRegisterModules());
        PublicBundleImportResult imported = new PublicBundleDatabaseImporter(
                jdbcTemplate, transactions, codec).importBundle(actualBundleSnapshot());
        releaseId = imported.getReleaseId();
    }

    @Test
    void actualImportedClaimProjectionRetainsAllSemanticFieldsAndApprovedEvidence() {
        List<PostgresKnowledgePassageRow> rows = new JdbcPostgresFactPassageQuery(jdbcTemplate)
                .findPassages(releaseId, List.of("sql-audit-project"));

        PostgresKnowledgePassageRow row = rows.stream()
                .filter(candidate -> candidate.getClaimId().equals("claim-sql-audit-delivered"))
                .findFirst()
                .orElseThrow();

        assertThat(row.getClaim().getCategory()).isEqualTo(
                com.portfolio.agent.answer.domain.AnswerClaimCategory.OUTCOME);
        assertThat(row.getClaim().getStatement()).contains("核心版本已完成");
        assertThat(row.getClaim().getDetail()).contains("不声明长期生产效果");
        assertThat(row.getClaim().getAchievementStatus().name()).isNotBlank();
        assertThat(row.getClaim().getContributionType().name()).isNotBlank();
        assertThat(row.getClaim().getVerificationBasis().name()).isNotBlank();
        assertThat(row.getClaim().getVerificationStatus()).isEqualTo(
                com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus.VERIFIED);
        assertThat(row.getClaim().getMateriality().name()).isNotBlank();
        assertThat(row.getClaim().getTopics()).isNotEmpty();
        assertThat(row.getEvidenceReferences()).isNotEmpty()
                .allSatisfy(reference -> assertThat(reference.getPublicStatus())
                        .isEqualTo("APPROVED"));
    }

    private com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot actualBundleSnapshot()
            throws Exception {
        PublicBundleLoader loader = new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(),
                Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC));
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json")) {
            try (InputStream stream = getClass().getClassLoader()
                    .getResourceAsStream("public-data/bundle/" + name)) {
                assertThat(stream).as("resource %s", name).isNotNull();
                files.put(name, stream.readAllBytes());
            }
        }
        return loader.load(files);
    }
}
