package com.portfolio.agent.answer.context.adapter.postgres;

import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.crypto.JdkContextEnvelopeCryptographyAdapter;
import com.portfolio.agent.answer.context.crypto.JdkResumeTokenHashAdapter;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.service.ConversationContextCapacityPolicy;
import com.portfolio.agent.answer.context.service.ConversationContextMutationFactory;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcConversationBusinessContextStoreIntegrationTest {
    private static final Instant START = Instant.parse("2026-08-12T00:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16-bookworm");

    private JdbcConversationBusinessContextStore store;

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context")
                .schemas("agent_context")
                .defaultSchema("agent_context")
                .table("flyway_schema_history_context")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/context")
                .schemas("agent_context")
                .defaultSchema("agent_context")
                .table("flyway_schema_history_context")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        store = new JdbcConversationBusinessContextStore(
                jdbc, transactions, ConversationContextCodecRegistry.defaults(),
                new JdkContextEnvelopeCryptographyAdapter("payload-v1", bytes((byte) 1), null, null),
                new JdkResumeTokenHashAdapter("token-v1", bytes((byte) 2), null, null),
                ConversationContextCapacityPolicy.defaults(), "agent_context");
    }

    @Test
    void persistsSecondRoundContextAndClearRemovesSessionAndHandles() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        store.open(conversationId, token, START);

        ConversationContextMutation first = mutation("first", null,
                ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        ConversationBusinessContextStore.SaveResult firstResult = store.save(
                conversationId, token, first, START.plusSeconds(1));
        assertTrue(firstResult.isActiveAdvanced());
        assertTrue(store.resolve(conversationId, token, first.getContextHandle(),
                START.plusSeconds(1)).isPresent());

        ConversationContextMutation second = mutation("second", first.getContextHandle(),
                ContextSlot.ACTIVE_FACT_CONTEXT, firstResult.getActiveRevision());
        ConversationBusinessContextStore.SaveResult secondResult = store.save(
                conversationId, token, second, START.plusSeconds(2));
        assertTrue(secondResult.isActiveAdvanced());
        assertEquals(second.getContextHandle(), store.active(
                conversationId, token, ContextSlot.ACTIVE_FACT_CONTEXT, START.plusSeconds(2))
                .orElseThrow().getContextHandle());
        assertEquals(2, store.list(conversationId, token, START.plusSeconds(2)).size());
        assertTrue(store.resolve(token, second.getContextHandle(), START.plusSeconds(2)).isPresent());

        store.clear(conversationId, token);

        assertTrue(store.findConversation(token).isEmpty());
        assertTrue(store.list(conversationId, token, START.plusSeconds(3)).isEmpty());
        assertFalse(store.resolve(token, second.getContextHandle(), START.plusSeconds(3)).isPresent());
    }

    private static ConversationContextMutation mutation(
            String sourceTaskId, com.portfolio.agent.answer.context.domain.ContextHandle parent,
            ContextSlot slot, long expectedRevision) {
        return new ConversationContextMutationFactory(
                ConversationContextCodecRegistry.defaults(), ConversationContextCapacityPolicy.defaults())
                .create(ConversationContextValue.recentSemanticTask(new RecentSemanticTaskContext(
                                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                                List.of(SubjectReference.project("project-a", "public-v1")),
                                Set.of("OVERVIEW"), Set.of(), "public-v1", sourceTaskId)),
                        parent, sourceTaskId, slot, expectedRevision);
    }

    private static byte[] bytes(byte value) {
        byte[] result = new byte[32];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
