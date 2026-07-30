package com.portfolio.agent.ingestion.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GovernanceSchemaMigrationTest {

    @Test
    void definesPrivateIncrementalIngestionTablesWithoutPublicRuntimeGrants() throws IOException {
        String migration = readResource("db/governance/V1__governance_ingestion_schema.sql");

        assertThat(migration)
                .contains("CREATE EXTENSION IF NOT EXISTS vector")
                .contains("CREATE TABLE import_run")
                .contains("CREATE TABLE source_document")
                .contains("CREATE TABLE source_revision")
                .contains("CREATE TABLE source_chunk")
                .contains("CREATE TABLE source_link_suggestion")
                .contains("embedding vector(512)")
                .doesNotContain("portfolio_runtime_reader");
    }

    private String readResource(String path) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertThat(stream).as("migration resource %s", path).isNotNull();
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
