package com.portfolio.agent.turn.state.postgres;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationSemanticStateMigrationTest {

    @Test void v7AddsOneAllOrNothingEncryptedSemanticStateToSessionAuthority()
            throws Exception {
        try (java.io.InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(
                        "db/context/V7__conversation_semantic_state.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "semantic_state_key_id",
                    "semantic_state_nonce",
                    "semantic_state_ciphertext",
                    "semantic_state_updated_at",
                    "conversation_session_semantic_state_shape")
                    .doesNotContain("user_text", "prompt", "model_body");
        }
    }
}
