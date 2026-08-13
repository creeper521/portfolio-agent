package com.portfolio.agent.answer.context.adapter.postgres;

import com.portfolio.agent.answer.context.domain.ContextStoreMode;
import com.portfolio.agent.answer.context.crypto.ContextEnvelopeCryptographyPort;
import com.portfolio.agent.answer.context.crypto.ResumeTokenHashPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationContextDatabaseConfigurationTest {
    @Test
    void postgresConfigurationIsOptInAndHasNoMemoryFallback() {
        ConditionalOnProperty condition = ConversationContextDatabaseConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);

        assertEquals("mode", condition.name()[0]);
        assertEquals("POSTGRESQL", condition.havingValue());
        assertEquals(ContextStoreMode.POSTGRESQL, ContextStoreMode.valueOf(condition.havingValue()));
        assertTrue(ConversationContextDatabaseConfiguration.class.getSimpleName().contains("Database"));
    }

    @Test
    void databasePropertiesFailClosedWhenPostgresCredentialsAreMissing() {
        ConversationContextDatabaseProperties properties = new ConversationContextDatabaseProperties();
        assertThrows(IllegalStateException.class, properties::validate);
        properties.setUrl("jdbc:postgresql://localhost/context");
        properties.setUsername("context");
        properties.setPassword("secret");
        properties.validate();
    }

    @Test
    void currentOnlyTokenAndPayloadKeysAsynchronouslyAssembleWithoutPreviousKeyIds() {
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.getCrypto().setCurrentTokenKeyId("token-current");
        properties.getCrypto().setCurrentTokenKey(base64(32));
        properties.getCrypto().setCurrentPayloadKeyId("payload-current");
        properties.getCrypto().setCurrentPayloadKey(base64(32));

        ConversationContextDatabaseConfiguration configuration =
                new ConversationContextDatabaseConfiguration();
        ResumeTokenHashPort tokenHash = configuration.conversationContextResumeTokenHash(properties);
        ContextEnvelopeCryptographyPort payload =
                configuration.conversationContextEnvelopeCryptography(properties);

        assertTrue(tokenHash != null);
        assertTrue(payload != null);
    }

    @Test
    void halfConfiguredPreviousKeyIsRejectedAtAssembly() {
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.getCrypto().setCurrentTokenKeyId("token-current");
        properties.getCrypto().setCurrentTokenKey(base64(32));
        properties.getCrypto().setPreviousTokenKeyId("token-previous");
        properties.getCrypto().setCurrentPayloadKeyId("payload-current");
        properties.getCrypto().setCurrentPayloadKey(base64(32));
        properties.getCrypto().setPreviousPayloadKeyId("payload-previous");

        ConversationContextDatabaseConfiguration configuration =
                new ConversationContextDatabaseConfiguration();

        assertThrows(IllegalArgumentException.class,
                () -> configuration.conversationContextResumeTokenHash(properties));
        assertThrows(IllegalArgumentException.class,
                () -> configuration.conversationContextEnvelopeCryptography(properties));
    }

    @Test
    void schemaContainsClosedTablesAndNoQuestionAnswerOrEvidenceTextColumns() throws IOException {
        String sql;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "db/context/V1__conversation_context_schema.sql")) {
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertTrue(sql.contains("conversation_session"));
        assertTrue(sql.contains("conversation_context"));
        assertTrue(sql.contains("conversation_active_context"));
        assertTrue(sql.contains("conversation_request_receipt"));
        assertTrue(sql.contains("context_type in ('recent_semantic_task', 'recommendation')"));
        assertTrue(sql.contains("active_slot in ('active_fact_context', 'active_compare_context', 'active_recommendation')"));
        assertTrue(sql.contains("on delete cascade"));
        assertTrue(sql.contains("octet_length(nonce) = 12"));
        assertTrue(sql.contains("payload_bytes between 1 and 16384"));
        assertTrue(!sql.matches("(?s).*\\bquestion\\b.*") || sql.contains("no question"));
        assertTrue(!sql.matches("(?s).*\\banswer\\b.*") || sql.contains("no answer"));
        assertTrue(!sql.matches("(?s).*\\bevidence\\b.*") || sql.contains("no evidence"));
    }

    private static String base64(int length) {
        return java.util.Base64.getEncoder().encodeToString(new byte[length]);
    }
}
