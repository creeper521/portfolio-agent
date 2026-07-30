package com.portfolio.agent.portfolio.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.service.PublicReleaseActivationService;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPublicPortfolioRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
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
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void importsActivatesAndReadsTheActualBundleWithoutLoss() throws Exception {
        RuntimeContentSnapshot expected = actualBundleSnapshot();
        PublicRuntimeSnapshotCodec codec =
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        PublicBundleImportResult imported =
                new PublicBundleDatabaseImporter(jdbcTemplate, transactions, codec).importBundle(expected);
        new PublicReleaseActivationService(jdbcTemplate, transactions).activate(imported.getReleaseId());
        TransactionTemplate readTransactions = transactions;
        readTransactions.setReadOnly(true);

        RuntimeContentSnapshot actual = new PostgresPublicPortfolioRepository(
                new JdbcPublicRuntimeSnapshotStore(jdbcTemplate), readTransactions, codec).getSnapshot();

        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM release_runtime_snapshot", Integer.class)).isEqualTo(1);
    }

    @Test
    void payloadFailureRollsBackNormalizedRowsAndLeavesTheOldActiveReleaseUnchanged() throws Exception {
        RuntimeContentSnapshot oldSnapshot = actualBundleSnapshot();
        PublicRuntimeSnapshotCodec codec =
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        PublicBundleDatabaseImporter importer =
                new PublicBundleDatabaseImporter(jdbcTemplate, transactions, codec);
        PublicBundleImportResult oldRelease = importer.importBundle(oldSnapshot);
        new PublicReleaseActivationService(jdbcTemplate, transactions).activate(oldRelease.getReleaseId());
        RuntimeContentSnapshot rejectedSnapshot = withReleaseMetadata(
                oldSnapshot, oldSnapshot.getContentVersion() + "-rejected", "f".repeat(64));
        String rejectedReleaseId = deterministicReleaseId(rejectedSnapshot);
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_runtime_snapshot_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'forced runtime snapshot rejection';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_runtime_snapshot
                BEFORE INSERT ON release_runtime_snapshot
                FOR EACH ROW EXECUTE FUNCTION reject_runtime_snapshot_insert()
                """);

        assertThatThrownBy(() -> importer.importBundle(rejectedSnapshot))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM content_release WHERE release_id = CAST(? AS uuid)
                """, Integer.class, rejectedReleaseId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM portfolio_subject WHERE release_id = CAST(? AS uuid)
                """, Integer.class, rejectedReleaseId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM retrieval_document WHERE release_id = CAST(? AS uuid)
                """, Integer.class, rejectedReleaseId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM release_runtime_snapshot WHERE release_id = CAST(? AS uuid)
                """, Integer.class, rejectedReleaseId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT release_id::text FROM active_release WHERE singleton = true
                """, String.class)).isEqualTo(oldRelease.getReleaseId());
    }

    @Test
    void repositoryAcceptsJsonbNumericRewritingButRejectsARealNumericChange() throws Exception {
        RuntimeContentSnapshot expected =
                withAverageDocumentLength(actualBundleSnapshot(), 100.0);
        PublicRuntimeSnapshotCodec codec =
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        PublicBundleImportResult imported =
                new PublicBundleDatabaseImporter(jdbcTemplate, transactions, codec).importBundle(expected);
        new PublicReleaseActivationService(jdbcTemplate, transactions).activate(imported.getReleaseId());
        TransactionTemplate readTransactions = transactions;
        readTransactions.setReadOnly(true);

        jdbcTemplate.update("""
                UPDATE release_runtime_snapshot
                SET payload = jsonb_set(
                    payload,
                    '{retrieval,keywordIndex,averageDocumentLength}',
                    '1e2'::jsonb,
                    false)
                WHERE release_id = CAST(? AS uuid)
                """, imported.getReleaseId());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT payload #>> '{retrieval,keywordIndex,averageDocumentLength}'
                FROM release_runtime_snapshot
                WHERE release_id = CAST(? AS uuid)
                """, String.class, imported.getReleaseId())).isEqualTo("100");

        RuntimeContentSnapshot loaded = new PostgresPublicPortfolioRepository(
                new JdbcPublicRuntimeSnapshotStore(jdbcTemplate), readTransactions, codec).getSnapshot();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(expected);

        jdbcTemplate.update("""
                UPDATE release_runtime_snapshot
                SET payload = jsonb_set(
                    payload,
                    '{retrieval,keywordIndex,averageDocumentLength}',
                    '101'::jsonb,
                    false)
                WHERE release_id = CAST(? AS uuid)
                """, imported.getReleaseId());

        assertThatThrownBy(() -> new PostgresPublicPortfolioRepository(
                new JdbcPublicRuntimeSnapshotStore(jdbcTemplate), readTransactions, codec).getSnapshot())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
    }

    private RuntimeContentSnapshot actualBundleSnapshot() throws Exception {
        PublicBundleLoader loader = new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(),
                Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC));
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json")) {
            files.put(name, readResource("public-data/bundle/" + name));
        }
        return loader.load(files);
    }

    private byte[] readResource(String name) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        assertThat(stream).as("resource %s", name).isNotNull();
        try (InputStream input = stream) {
            return input.readAllBytes();
        }
    }

    private RuntimeContentSnapshot withReleaseMetadata(
            RuntimeContentSnapshot source,
            String contentVersion,
            String runtimeBundleHash) {
        PortfolioSnapshot content = new PortfolioSnapshot(
                source.getSchemaVersion(), contentVersion, source.getPublishedAt(), source.getOwner(),
                source.getProjects(), source.getCases(), source.getCollections(), source.getClaims(),
                source.getClaimEvidenceLinks(), source.getQuestions(), source.getApprovedEvidence(),
                source.getTimeline());
        return new RuntimeContentSnapshot(
                content, runtimeBundleHash, source.getLoadedAt(),
                source.getRetrievalContent().orElseThrow());
    }

    private RuntimeContentSnapshot withAverageDocumentLength(
            RuntimeContentSnapshot source,
            double averageDocumentLength) {
        RuntimeRetrievalContent retrieval = source.getRetrievalContent().orElseThrow();
        RuntimeKeywordIndex keywordIndex = retrieval.getKeywordIndex();
        RuntimeKeywordIndex changedKeywordIndex = new RuntimeKeywordIndex(
                keywordIndex.getDocumentCount(),
                averageDocumentLength,
                keywordIndex.getDocuments(),
                keywordIndex.getDocumentFrequencies());
        RuntimeRetrievalContent changedRetrieval = new RuntimeRetrievalContent(
                retrieval.getManifest(),
                retrieval.getDocuments(),
                changedKeywordIndex,
                retrieval.getVectorIndex());
        return new RuntimeContentSnapshot(
                new PortfolioSnapshot(
                        source.getSchemaVersion(),
                        source.getContentVersion(),
                        source.getPublishedAt(),
                        source.getOwner(),
                        source.getProjects(),
                        source.getCases(),
                        source.getCollections(),
                        source.getClaims(),
                        source.getClaimEvidenceLinks(),
                        source.getQuestions(),
                        source.getApprovedEvidence(),
                        source.getTimeline()),
                source.getRuntimeBundleHash(),
                source.getLoadedAt(),
                changedRetrieval);
    }

    private String deterministicReleaseId(RuntimeContentSnapshot snapshot) {
        return UUID.nameUUIDFromBytes((
                snapshot.getSchemaVersion() + "\n"
                        + snapshot.getContentVersion() + "\n"
                        + snapshot.getRuntimeBundleHash())
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
