package com.portfolio.agent.turn.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTurnScenarioManifestTest {

    private static final Set<String> COMMAND_KINDS = Set.of(
            "ASK", "CONTINUE", "RESOLVE_CLARIFICATION");
    private static final Set<String> TURN_KINDS = Set.of(
            "ANSWER", "CLARIFICATION", "CONVERSATIONAL", "BOUNDARY",
            "CAPABILITY_UNAVAILABLE");
    private static final Set<String> RESOLUTIONS = Set.of(
            "COMPLETE", "PARTIAL", "NO_RESULT");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "taskId", "taskType", "dependency", "dependencies", "sourceTaskIds",
            "agentTurnContract", "contractVersion", "degraded", "completedTasks");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void manifestsDescribeAtLeastThirtyTargetBehaviorScenarios() throws Exception {
        Path directory = repositoryRoot().resolve("contracts/agent-turn/scenarios");
        assertThat(directory).isDirectory();

        List<Path> manifests;
        try (Stream<Path> files = Files.list(directory)) {
            manifests = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }

        assertThat(manifests).hasSize(7);
        Set<String> caseIds = new HashSet<>();
        int scenarioCount = 0;
        for (Path manifest : manifests) {
            JsonNode root = mapper.readTree(manifest.toFile());
            assertThat(root.path("schemaVersion").asText())
                    .as("schemaVersion in %s", manifest)
                    .isEqualTo("agent-turn-scenarios-v1");
            assertThat(root.path("group").asText()).isNotBlank();
            JsonNode scenarios = root.path("scenarios");
            assertThat(scenarios.isArray()).isTrue();
            assertThat(scenarios).isNotEmpty();
            for (JsonNode scenario : scenarios) {
                scenarioCount++;
                validateScenario(manifest, scenario, caseIds);
            }
        }

        assertThat(scenarioCount).isBetween(30, 40);
        assertThat(caseIds).hasSize(scenarioCount);
    }

    private void validateScenario(Path manifest, JsonNode scenario, Set<String> caseIds) {
        String caseId = scenario.path("caseId").asText();
        assertThat(caseId).as("caseId in %s", manifest).matches("[a-z0-9][a-z0-9.-]{4,95}");
        assertThat(caseIds.add(caseId)).as("unique caseId %s", caseId).isTrue();

        JsonNode command = scenario.path("command");
        assertThat(COMMAND_KINDS).contains(command.path("kind").asText());
        if ("ASK".equals(command.path("kind").asText())) {
            assertThat(Set.of("FREE_TEXT", "PRESET"))
                    .contains(command.path("inputKind").asText());
        }

        assertThat(scenario.path("contextSummary").isObject()).isTrue();
        assertThat(scenario.path("requiredCapabilities").isArray()).isTrue();
        assertThat(scenario.path("hardErrorExpectations").isArray()).isTrue();

        JsonNode expected = scenario.path("expected");
        if (expected.has("apiError")) {
            assertThat(expected.has("kind")).isFalse();
            assertThat(expected.path("apiError").path("status").asInt()).isBetween(400, 599);
            assertThat(expected.path("apiError").path("code").asText()).isNotBlank();
            assertThat(expected.has("resolution")).isFalse();
            assertNoForbiddenKeys(scenario, caseId);
            return;
        }
        if ("NONE".equals(expected.path("publication").asText())) {
            assertThat(expected.has("kind")).isFalse();
            assertThat(expected.has("resolution")).isFalse();
            assertNoForbiddenKeys(scenario, caseId);
            return;
        }
        String turnKind = expected.path("kind").asText();
        assertThat(TURN_KINDS).contains(turnKind);
        if ("ANSWER".equals(turnKind)) {
            assertThat(RESOLUTIONS).contains(expected.path("resolution").asText());
            assertThat(expected.path("goals").isArray()).isTrue();
            assertThat(expected.path("goals")).isNotEmpty();
        } else {
            assertThat(expected.has("resolution")).isFalse();
        }
        assertNoForbiddenKeys(scenario, caseId);
    }

    private void assertNoForbiddenKeys(JsonNode node, String caseId) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name -> {
                assertThat(FORBIDDEN_KEYS)
                        .as("forbidden internal key %s in %s", name, caseId)
                        .doesNotContain(name);
                assertNoForbiddenKeys(node.get(name), caseId);
            });
        } else if (node.isArray()) {
            node.forEach(child -> assertNoForbiddenKeys(child, caseId));
        }
    }

    private Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("backend"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("backend"))) {
            return parent;
        }
        throw new IOException("repository root not found from " + current);
    }
}
