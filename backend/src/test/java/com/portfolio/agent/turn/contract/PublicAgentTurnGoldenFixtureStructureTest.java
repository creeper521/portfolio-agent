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

class PublicAgentTurnGoldenFixtureStructureTest {

    private static final Set<String> EXPECTED_FILES = Set.of(
            "answer-complete.json", "answer-partial.json", "answer-no-result.json",
            "answer-local-clarification.json", "clarification.json", "conversational.json",
            "boundary.json", "capability-unavailable.json");
    private static final Set<String> TURN_KINDS = Set.of(
            "ANSWER", "CLARIFICATION", "CONVERSATIONAL", "BOUNDARY",
            "CAPABILITY_UNAVAILABLE");
    private static final Set<String> COVERAGES = Set.of("FULL", "PARTIAL", "NONE");
    private static final Set<String> PRESENTATION_KINDS = Set.of("SECTIONED", "RECOMMENDATION");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "interaction", "agentTurn", "contractVersion", "disposition", "completedTasks",
            "taskId", "sourceTaskId", "sourceTaskIds", "claimId", "claimIds", "evidenceId",
            "evidenceIds", "degraded", "degradationSummary", "execution", "reasonCodes");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fixturesDefineTheClosedPublicAgentTurnContract() throws Exception {
        Path directory = repositoryRoot().resolve("contracts/agent-turn/fixtures");
        assertThat(directory).isDirectory();

        List<Path> fixtures;
        try (Stream<Path> files = Files.list(directory)) {
            fixtures = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertThat(fixtures).extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_FILES);

        for (Path fixture : fixtures) {
            JsonNode turn = mapper.readTree(fixture.toFile());
            validateTurn(fixture.getFileName().toString(), turn);
        }
    }

    private void validateTurn(String fileName, JsonNode turn) {
        assertThat(turn.path("requestId").asText()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        String kind = turn.path("kind").asText();
        assertThat(TURN_KINDS).contains(kind);
        assertNoForbiddenKeys(turn, fileName);

        if ("ANSWER".equals(kind)) {
            assertThat(turn.has("answer")).isTrue();
            validateAnswer(fileName, turn.path("answer"));
        } else {
            assertThat(turn.has("answer")).isFalse();
            assertThat(turn.path("message").asText()).isNotBlank();
        }

        if ("CLARIFICATION".equals(kind)) {
            validateChallenge(turn.path("clarification"));
        }
    }

    private void validateAnswer(String fileName, JsonNode answer) {
        String resolution = answer.path("resolution").asText();
        assertThat(Set.of("COMPLETE", "PARTIAL", "NO_RESULT")).contains(resolution);
        assertThat(answer.path("contentReleaseId").asText()).isEqualTo("2026-08-05.1");
        assertThat(answer.path("goalResults").isArray()).isTrue();
        assertThat(answer.path("goalResults")).isNotEmpty();

        Set<String> sourceKeys = sourceKeys(answer.path("sourceCatalog"));
        int producedGoals = 0;
        int fullGoals = 0;
        for (JsonNode goal : answer.path("goalResults")) {
            String coverage = goal.path("coverage").asText();
            assertThat(COVERAGES).contains(coverage);
            assertThat(goal.path("goalId").asText()).isNotBlank();
            assertThat(goal.path("label").asText()).isNotBlank();
            boolean hasPresentation = goal.has("presentation");
            boolean hasNotices = goal.path("notices").isArray() && !goal.path("notices").isEmpty();
            if ("FULL".equals(coverage)) {
                fullGoals++;
                producedGoals++;
                assertThat(hasPresentation).isTrue();
            } else if ("PARTIAL".equals(coverage)) {
                producedGoals++;
                assertThat(hasPresentation).isTrue();
                assertThat(hasNotices).isTrue();
            } else {
                assertThat(hasPresentation).isFalse();
                assertThat(hasNotices).isTrue();
            }
            if (hasPresentation) {
                validatePresentation(goal.path("presentation"), sourceKeys);
            }
        }

        if ("COMPLETE".equals(resolution)) {
            assertThat(fullGoals).isEqualTo(answer.path("goalResults").size());
        } else if ("PARTIAL".equals(resolution)) {
            assertThat(producedGoals).isPositive();
            assertThat(fullGoals).isLessThan(answer.path("goalResults").size());
        } else {
            assertThat(producedGoals).isZero();
        }

        if ("answer-local-clarification.json".equals(fileName)) {
            validateChallenge(answer.path("localClarification"));
            assertThat(answer.path("localClarification").path("affectedGoalIds")).isNotEmpty();
        }
    }

    private Set<String> sourceKeys(JsonNode catalog) {
        assertThat(catalog.isObject()).isTrue();
        assertThat(catalog.path("sources").isArray()).isTrue();
        Set<String> keys = new HashSet<>();
        for (JsonNode source : catalog.path("sources")) {
            String key = source.path("key").asText();
            assertThat(key).isNotBlank();
            assertThat(keys.add(key)).isTrue();
            assertThat(source.path("label").asText()).isNotBlank();
            assertThat(source.path("route").asText()).startsWith("/");
        }
        return keys;
    }

    private void validatePresentation(JsonNode presentation, Set<String> sourceKeys) {
        assertThat(PRESENTATION_KINDS).contains(presentation.path("kind").asText());
        if ("SECTIONED".equals(presentation.path("kind").asText())) {
            assertThat(presentation.path("sections").isArray()).isTrue();
            assertThat(presentation.path("sections")).isNotEmpty();
            presentation.path("sections").forEach(section -> validateSupport(section.path("support"), sourceKeys));
        } else {
            assertThat(presentation.path("items").isArray()).isTrue();
            presentation.path("items").forEach(item -> validateSupport(item.path("support"), sourceKeys));
        }
    }

    private void validateSupport(JsonNode support, Set<String> sourceKeys) {
        assertThat(Set.of("GENERAL_KNOWLEDGE", "VERIFIED_PUBLIC_EVIDENCE", "DERIVED"))
                .contains(support.path("kind").asText());
        assertThat(support.path("publicSourceKeys").isArray()).isTrue();
        support.path("publicSourceKeys").forEach(key -> assertThat(sourceKeys).contains(key.asText()));
    }

    private void validateChallenge(JsonNode challenge) {
        assertThat(challenge.path("clarificationId").asText()).isNotBlank();
        assertThat(challenge.path("prompt").asText()).isNotBlank();
        assertThat(challenge.path("fields").isArray()).isTrue();
        assertThat(challenge.path("fields")).isNotEmpty();
        challenge.path("fields").forEach(field -> assertThat(Set.of("SINGLE_CHOICE", "TEXT"))
                .contains(field.path("kind").asText()));
    }

    private void assertNoForbiddenKeys(JsonNode node, String fileName) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name -> {
                assertThat(FORBIDDEN_KEYS)
                        .as("forbidden key %s in %s", name, fileName)
                        .doesNotContain(name);
                assertNoForbiddenKeys(node.get(name), fileName);
            });
        } else if (node.isArray()) {
            node.forEach(child -> assertNoForbiddenKeys(child, fileName));
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
