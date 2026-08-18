package com.portfolio.agent.turn.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TurnModuleDependencyTest {
    @Test void coreModulesDoNotDependOnHttpDtosOrReverseLifecycleEdges() throws Exception {
        Path root = Path.of("src/main/java/com/portfolio/agent/turn");
        List<String> planning = sources(root.resolve("planning"));
        List<String> execution = sources(root.resolve("execution"));
        List<String> capability = sources(root.resolve("capability"));
        List<String> projection = sources(root.resolve("projection"));

        assertThat(planning).noneMatch(value -> value.contains("turn.api")
                || value.contains("turn.execution") || value.contains("turn.capability"));
        assertThat(execution).noneMatch(value -> value.contains("turn.api")
                || value.contains("turn.lifecycle"));
        assertThat(capability).noneMatch(value -> value.contains("turn.api")
                || value.contains("turn.lifecycle"));
        assertThat(projection).noneMatch(value -> value.contains("turn.api")
                || value.contains("turn.infrastructure") || value.contains("answer.dto"));
    }

    @Test void productionTurnModuleContainsNoMigrationOrVersionBridgeNames() throws Exception {
        List<String> sources = sources(Path.of("src/main/java/com/portfolio/agent/turn"));
        assertThat(sources).noneMatch(value ->
                value.matches("(?s).*\\b(Legacy|Compatibility|Migration|Shadow|stp-v[123])\\b.*"));
    }

    private List<String> sources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (Exception failure) { throw new IllegalStateException(failure); }
                    }).toList();
        }
    }
}
