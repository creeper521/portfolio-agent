package com.portfolio.agent.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.intelligence.adapter.postgres.JdbcPostgresKnowledgeQuery;
import com.portfolio.agent.answer.intelligence.adapter.postgres.PostgresPortfolioRetriever;
import com.portfolio.agent.answer.intelligence.execution.adapter.bundle.BundlePortfolioCandidateRetrievalAdapter;
import com.portfolio.agent.answer.intelligence.execution.capability.DefaultPortfolioEvidenceCapability;
import com.portfolio.agent.answer.intelligence.execution.capability.PortfolioEvidenceCapability;
import com.portfolio.agent.answer.intelligence.execution.planning.PortfolioCapabilityCatalog;
import com.portfolio.agent.answer.routing.adapter.execution.P3PortfolioSemanticTaskExecutor;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleDatabaseImporter;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleImportResult;
import com.portfolio.agent.portfolio.repository.postgres.PublicRuntimeSnapshotCodec;
import com.portfolio.agent.portfolio.service.PublicReleaseActivationService;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
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

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class P3PostgresPortfolioExecutionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");

    private JdbcTemplate jdbcTemplate;
    private String contentVersion;

    @BeforeEach
    void setUp() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/public")
                .table("flyway_schema_history_public")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/public")
                .table("flyway_schema_history_public")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        RuntimeContentSnapshot snapshot = actualBundleSnapshot();
        PublicBundleImportResult imported = new PublicBundleDatabaseImporter(
                jdbcTemplate, transactions,
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules()))
                .importBundle(snapshot);
        new PublicReleaseActivationService(jdbcTemplate, transactions).activate(imported.getReleaseId());
        contentVersion = imported.getReleaseVersion();
    }

    @Test
    void executesRealSqlProjectThroughP3WithoutRejectingSharedEvidence() {
        Integer sharedEvidenceCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT cel.evidence_stable_id
                    FROM claim_evidence_link cel
                    JOIN claim c ON c.release_id = cel.release_id
                        AND c.stable_id = cel.claim_stable_id
                    WHERE c.subject_stable_id = 'sql-audit-project'
                    GROUP BY cel.evidence_stable_id
                    HAVING count(DISTINCT cel.claim_stable_id) > 1
                ) shared
                """, Integer.class);
        assertThat(sharedEvidenceCount).isGreaterThan(0);

        BundlePortfolioCandidateRetrievalAdapter primary = new BundlePortfolioCandidateRetrievalAdapter(
                new PostgresPortfolioRetriever(new JdbcPostgresKnowledgeQuery(jdbcTemplate,
                        ignored -> new EmbeddingVector(new float[] {0.0f}))));
        BundlePortfolioCandidateRetrievalAdapter fallback = new BundlePortfolioCandidateRetrievalAdapter(
                new PostgresPortfolioRetriever(new JdbcPostgresKnowledgeQuery(jdbcTemplate,
                        ignored -> new EmbeddingVector(new float[] {0.0f}))));
        SubjectReference subject = SubjectReference.project("sql-audit-project", contentVersion);
        PortfolioEvidenceCapability capability = new DefaultPortfolioEvidenceCapability(primary, fallback);
        P3PortfolioSemanticTaskExecutor executor = new P3PortfolioSemanticTaskExecutor(
                new PortfolioCapabilityCatalog(), capability);
        SemanticTask task = SemanticTask.create(
                "p3-sql-project", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "SQL audit project overview",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of("OVERVIEW"), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(), List.of(subject));

        TaskOutcome outcome = executor.execute(new SemanticTaskExecutionContext(
                task, List.of(), List.of(), contentVersion,
                TaskExecutionAllowance.portfolio(Instant.now().plusSeconds(30)), List.of()));

        assertThat(outcome.getExecutionStatus())
                .withFailMessage("resolution=%s reasonCodes=%s", outcome.getResolution(), outcome.getReasonCodes())
                .isEqualTo(TaskOutcome.TaskExecutionStatus.SUCCEEDED);
        assertThat(outcome.getResolution()).isEqualTo(TaskOutcome.TaskResolution.ANSWERED);
        assertThat(outcome.getReasonCodes()).doesNotContain("EVIDENCE_INTEGRITY_FAILURE");
        assertThat(outcome.getContribution()).isPresent();
        assertThat(outcome.getContribution().orElseThrow().getSourceReferences())
                .isNotEmpty()
                .allSatisfy(reference -> {
                    assertThat(reference.getReferenceKey()).startsWith("E-");
                    assertThat(reference.getLabel()).isNotBlank();
                    assertThat(reference.getEvidenceRoute()).startsWith("/evidence?evidence=");
                });
    }

    private RuntimeContentSnapshot actualBundleSnapshot() throws Exception {
        PublicBundleLoader loader = new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(), new PortfolioSnapshotValidator(),
                Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC));
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of("manifest.json", "portfolio.json", "presentation.json",
                "rag-documents.jsonl", "keyword-index.json", "vector-index.bin", "checksums.json")) {
            try (InputStream stream = getClass().getClassLoader()
                    .getResourceAsStream("public-data/bundle/" + name)) {
                assertThat(stream).as("resource %s", name).isNotNull();
                files.put(name, stream.readAllBytes());
            }
        }
        return loader.load(files);
    }
}
