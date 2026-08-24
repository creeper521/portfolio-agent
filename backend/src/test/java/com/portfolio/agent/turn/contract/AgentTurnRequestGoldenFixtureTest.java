package com.portfolio.agent.turn.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.api.request.AgentTurnRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTurnRequestGoldenFixtureTest {

    private static final Set<String> EXPECTED_FILES = Set.of(
            "turn-request-glm.json",
            "turn-request-qwen.json",
            "turn-request-none.json",
            "turn-request-invalid-model-selection.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void fixturesDefineClosedModelSelectionRequests() throws Exception {
        Path directory = repositoryRoot().resolve("contracts/agent-turn/request-fixtures");
        assertThat(directory).isDirectory();

        List<Path> fixtures;
        try (Stream<Path> files = Files.list(directory)) {
            fixtures = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertThat(fixtures).extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_FILES);

        for (Path fixture : fixtures) {
            if (fixture.getFileName().toString().contains("invalid")) {
                assertThatThrownBy(() -> mapper.readValue(fixture.toFile(), AgentTurnRequest.class))
                        .isInstanceOf(Exception.class);
            } else {
                AgentTurnRequest request = mapper.readValue(fixture.toFile(), AgentTurnRequest.class);
                assertThat(validator.validate(request)).isEmpty();
            }
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
