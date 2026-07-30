package com.portfolio.agent.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IngestionArchitectureTest {

    @Test
    void doesNotDependOnAnswerRetrievalAdapters() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/portfolio/agent/ingestion");

        try (java.util.stream.Stream<Path> files = Files.walk(sourceRoot)) {
            assertThat(files.filter(Files::isRegularFile)
                    .map(this::read)
                    .anyMatch(source -> source.contains("com.portfolio.agent.answer.adapter")))
                    .isFalse();
        }
    }

    @Test
    void doesNotReusePortfolioReleaseEmbeddingPort() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/portfolio/agent/ingestion");

        try (java.util.stream.Stream<Path> files = Files.walk(sourceRoot)) {
            assertThat(files.filter(Files::isRegularFile)
                    .map(this::read)
                    .anyMatch(source -> source.contains("com.portfolio.agent.portfolio.release")))
                    .isFalse();
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
