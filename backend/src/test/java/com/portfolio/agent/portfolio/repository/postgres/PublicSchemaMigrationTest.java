package com.portfolio.agent.portfolio.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PublicSchemaMigrationTest {

    @Test
    void definesReleaseScopedPublicTablesAndPgvectorColumn() throws IOException {
        String migration = readResource("db/public/V1__public_release_schema.sql");

        assertThat(migration)
                .contains("CREATE EXTENSION IF NOT EXISTS vector")
                .contains("CREATE TABLE content_release")
                .contains("CREATE TABLE portfolio_subject")
                .contains("CREATE TABLE claim")
                .contains("CREATE TABLE evidence")
                .contains("CREATE TABLE claim_evidence_link")
                .contains("CREATE TABLE subject_capability")
                .contains("CREATE TABLE retrieval_document")
                .contains("embedding vector(512)")
                .contains("CREATE TABLE active_release");
    }

    private String readResource(String path) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertThat(stream).as("migration resource %s", path).isNotNull();
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
