package com.portfolio.agent.turn.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationContractGoldenFixtureStructureTest {
    private static final Set<String> EXPECTED = Set.of(
            "conversation-active.json",
            "conversation-expired.json",
            "turn-envelope-discussion-unavailable.json",
            "turn-envelope-discussion-context-unavailable.json",
            "turn-envelope-discussion-context-mismatch.json",
            "turn-envelope-discussion-subject-unavailable.json",
            "turn-envelope-discussion-context-expired.json",
            "turn-envelope-free-text-unavailable.json");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void fixturesFreezeRevisionAndBackendOwnedDiscussionActions()
            throws Exception {
        Path directory = repositoryRoot().resolve(
                "contracts/agent-turn/conversation-fixtures");
        assertThat(directory).isDirectory();
        java.util.List<Path> files;
        try (Stream<Path> values = Files.list(directory)) {
            files = values.filter(path -> path.toString().endsWith(".json"))
                    .sorted().toList();
        }
        assertThat(files).extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrderElementsOf(EXPECTED);
        for (Path file : files) {
            JsonNode root = mapper.readTree(file.toFile());
            JsonNode conversation = root.has("conversation")
                    ? root.path("conversation") : root;
            assertThat(conversation.path("conversationId").asText())
                    .isNotBlank();
            assertThat(conversation.path("discussionRevision").canConvertToLong())
                    .isTrue();
            assertThat(conversation.path("discussionRevision").asLong())
                    .isNotNegative();
            JsonNode discussion = conversation.path("activeDiscussion");
            assertThat(discussion.path("routeContinuation")
                    .path("operation").asText())
                    .isEqualTo("ROUTE_IN_CONTEXT");
            assertThat(root.toString()).doesNotContain(
                    "resumeToken", "question", "prompt", "modelOutput");
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (Files.isDirectory(current.resolve("contracts/agent-turn"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root is unavailable");
    }
}
