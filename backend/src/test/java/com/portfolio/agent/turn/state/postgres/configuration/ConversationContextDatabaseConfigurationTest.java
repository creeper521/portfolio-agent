package com.portfolio.agent.turn.state.postgres.configuration;

import com.portfolio.agent.turn.state.configuration.ConversationContextProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.infrastructure.AgentRuntimeProperties;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.state.postgres.AgentStatePayloadCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ConversationContextDatabaseConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConversationContextDatabaseConfiguration.class);

    @Test
    void postgresqlModeFailsClosedWhenDatabaseConfigurationIsMissing() {
        contextRunner.withPropertyValues("portfolio.conversation-context.mode=POSTGRESQL")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("PORTFOLIO_CONTEXT_DATABASE_URL is required");
                });
    }

    @Test
    void databaseSchemaDefaultsToTheMigrationOwnedSchema() {
        ConversationContextDatabaseProperties properties =
                new ConversationContextDatabaseProperties();
        properties.setUrl("jdbc:postgresql://127.0.0.1:54329/portfolio_context_dev");
        properties.setUsername("portfolio_context_owner");
        properties.setPassword("context-secret");

        properties.validate();

        assertThat(properties.getSchema()).isEqualTo("agent_context");
    }

    @Test
    void rejectsSchemaNamesNotOwnedByTheFixedMigrations() {
        ConversationContextDatabaseProperties properties = databaseProperties();
        properties.setSchema("custom_agent_state");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PORTFOLIO_CONTEXT_DATABASE_SCHEMA must be agent_context");
    }

    @Test
    void inMemoryAndDisabledModesExcludeEveryPostgresqlBean() {
        for (String mode : new String[]{"IN_MEMORY", "DISABLED"}) {
            contextRunner.withPropertyValues("portfolio.conversation-context.mode=" + mode)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean("conversationContextDataSource");
                        assertThat(context).doesNotHaveBean("conversationContextFlyway");
                        assertThat(context).doesNotHaveBean(AgentStatePayloadCodec.class);
                        assertThat(context).doesNotHaveBean(AgentStateStore.class);
                        assertThat(context).doesNotHaveBean(ConversationSessionStore.class);
                    });
        }
    }

    @Test
    void validConfigurationBuildsTheDedicatedStateInfrastructure() {
        ConversationContextDatabaseConfiguration configuration =
                new ConversationContextDatabaseConfiguration();
        ConversationContextDatabaseProperties database = databaseProperties();
        ConversationContextProperties state = stateProperties();
        database.validate();
        DataSource dataSource = mock(DataSource.class);
        PlatformTransactionManager manager = configuration.transactionManager(dataSource);
        TransactionTemplate transactions = configuration.transactions(manager);
        JdbcTemplate jdbc = configuration.jdbc(dataSource, mock(Flyway.class));
        AgentStatePayloadCodec codec = configuration.agentStatePayloadCodec(
                new ObjectMapper().findAndRegisterModules(), state);
        AgentRuntimeProperties runtime = new AgentRuntimeProperties();

        AgentStateStore agentStateStore = configuration.jdbcAgentStateStore(
                jdbc, transactions, codec, state, database, runtime);
        ConversationSessionStore sessionStore = configuration.jdbcConversationSessionStore(
                jdbc, transactions, state, database, runtime, codec);

        assertThat(agentStateStore).isNotNull();
        assertThat(sessionStore).isNotNull();
    }

    @Test
    void statePoolCoversActiveTurnsAndBoundsConnectionAcquisition() {
        ConversationContextDatabaseConfiguration configuration =
                new ConversationContextDatabaseConfiguration();
        AgentRuntimeProperties runtime = new AgentRuntimeProperties();

        com.zaxxer.hikari.HikariConfig hikari = configuration.hikariConfig(
                databaseProperties(), runtime);

        assertThat(hikari.getMaximumPoolSize()).isEqualTo(10);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(3000);
        assertThat(hikari.getDataSourceProperties())
                .containsEntry("connectTimeout", "3")
                .containsEntry("socketTimeout", "3")
                .containsEntry("cancelSignalTimeout", "3")
                .containsEntry("tcpKeepAlive", "true");
    }

    @Test
    void rejectsIdenticalTokenAndPayloadKeyMaterial() {
        ConversationContextDatabaseConfiguration configuration =
                new ConversationContextDatabaseConfiguration();
        ConversationContextProperties state = stateProperties();
        state.getCrypto().setCurrentPayloadKey(state.getCrypto().getCurrentTokenKey());

        assertThatThrownBy(() -> configuration.agentStatePayloadCodec(
                new ObjectMapper().findAndRegisterModules(), state))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("token and payload keys must be different");
    }

    @Test
    void rejectsIdenticalTokenAndPayloadKeyIds() {
        ConversationContextProperties state = stateProperties();
        state.getCrypto().setCurrentPayloadKeyId(
                state.getCrypto().getCurrentTokenKeyId());

        assertThatThrownBy(state::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("token and payload key ids must be different");
    }

    @Test
    void declaresOneDedicatedDataSourceAndFlywayAuthority() throws Exception {
        Method dataSource = ConversationContextDatabaseConfiguration.class
                .getDeclaredMethod(
                        "dataSource", ConversationContextDatabaseProperties.class,
                        AgentRuntimeProperties.class);
        Method flyway = ConversationContextDatabaseConfiguration.class
                .getDeclaredMethod(
                        "flyway", DataSource.class, ConversationContextDatabaseProperties.class);

        assertThat(dataSource.getAnnotation(Bean.class).name())
                .containsExactly("conversationContextDataSource");
        assertThat(flyway.getAnnotation(Bean.class).name())
                .containsExactly("conversationContextFlyway");
    }

    private ConversationContextDatabaseProperties databaseProperties() {
        ConversationContextDatabaseProperties properties =
                new ConversationContextDatabaseProperties();
        properties.setUrl("jdbc:postgresql://127.0.0.1:54329/portfolio_context_dev");
        properties.setUsername("portfolio_context_owner");
        properties.setPassword("context-secret");
        properties.setSchema("agent_context");
        return properties;
    }

    private ConversationContextProperties stateProperties() {
        byte[] tokenBytes = new byte[32];
        byte[] payloadBytes = new byte[32];
        java.util.Arrays.fill(payloadBytes, (byte) 1);
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.setMode(ConversationContextProperties.Mode.POSTGRESQL);
        properties.getCrypto().setCurrentTokenKeyId("token-v1");
        properties.getCrypto().setCurrentTokenKey(Base64.getEncoder().encodeToString(tokenBytes));
        properties.getCrypto().setCurrentPayloadKeyId("payload-v1");
        properties.getCrypto().setCurrentPayloadKey(
                Base64.getEncoder().encodeToString(payloadBytes));
        return properties;
    }
}
