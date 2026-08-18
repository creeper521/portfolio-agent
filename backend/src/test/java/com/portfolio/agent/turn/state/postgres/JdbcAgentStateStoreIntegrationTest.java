package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAgentStateStoreIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");
    private JdbcAgentStateStore store;
    private JdbcTemplate jdbc;
    private final Instant now = Instant.parse("2026-08-18T00:00:00Z");

    @BeforeEach void setUp() {
        Flyway.configure().dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context").schemas("agent_context")
                .defaultSchema("agent_context").table("flyway_schema_history_context")
                .cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context").schemas("agent_context")
                .defaultSchema("agent_context").table("flyway_schema_history_context")
                .load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcAgentStateStore(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new AgentStatePayloadCodec(
                        JsonMapper.builder().addModule(new ParameterNamesModule())
                                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build(),
                        "state-key-1", new byte[32]),
                "agent_context", Duration.ofMinutes(30), "token-key-1");
    }

    @Test void claimCompleteAndReplayUseOneEncryptedTerminalRow() {
        UUID requestId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();
        byte[] fingerprint = new byte[32];
        assertThat(store.claim(
                requestId, conversationId, fingerprint, now, Duration.ofSeconds(10)).status())
                .isEqualTo(TurnExecutionStore.ClaimResult.Status.CLAIMED);
        PublicAgentTurn snapshot = new PublicAgentTurn.Conversational(
                requestId, "不可明文保存的最终公开文本", List.of());
        assertThat(store.complete(
                requestId, fingerprint, snapshot, List.of(), List.of(), null,
                now.plusSeconds(1))).isTrue();
        TurnExecutionStore.ClaimResult replay = store.claim(
                requestId, conversationId, fingerprint, now.plusSeconds(2), Duration.ofSeconds(10));
        assertThat(replay.status()).isEqualTo(TurnExecutionStore.ClaimResult.Status.REPLAY);
        assertThat(((PublicAgentTurn.Conversational) replay.replay()).getMessage())
                .isEqualTo("不可明文保存的最终公开文本");
        byte[] ciphertext = jdbc.queryForObject(
                "SELECT settlement_ciphertext FROM agent_context.agent_turn_execution WHERE request_id=?",
                byte[].class, requestId);
        assertThat(new String(ciphertext, java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("不可明文保存的最终公开文本");
    }
}
