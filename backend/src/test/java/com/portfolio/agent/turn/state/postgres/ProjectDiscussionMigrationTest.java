package com.portfolio.agent.turn.state.postgres;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDiscussionMigrationTest {

    @Test
    void v4AddsOneAllOrNothingDiscussionPointerToTheSessionAuthority()
            throws Exception {
        try (java.io.InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(
                        "db/context/V4__project_discussion_context.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains(
                            "active_discussion_handle",
                            "active_discussion_project_id",
                            "active_discussion_expires_at",
                            "conversation_session_discussion_pointer_shape")
                    .doesNotContain(
                            "CREATE TABLE agent_context.project_discussion",
                            "compatibility",
                            "legacy");
        }
    }
}
