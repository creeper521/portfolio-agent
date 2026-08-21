package com.portfolio.agent.infrastructure.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemPromptCatalogTest {
    private static final String GOAL_PATH = "prompts/goal-interpretation-system.txt";
    private static final String GENERAL_PATH = "prompts/general-knowledge-system.txt";

    @Test void loadsAndNormalizesBothProductionPrompts() {
        SystemPromptCatalog catalog = new SystemPromptCatalog();

        assertThat(catalog.goalInterpretation())
                .contains(
                        "SEMANTIC_ROUTE", "STANDARD_GOAL",
                        "interpretationMode", "allowedRoutes",
                        "Simplified Chinese", "Depth selection",
                        "Calculate every anchor start",
                        "untrusted content")
                .doesNotContain(
                        "{\"kind\":\"GOALS\"",
                        "{\"kind\":\"CLARIFICATION\"")
                .doesNotStartWith(" ").doesNotEndWith(" ");
        assertThat(catalog.generalKnowledge())
                .contains(
                        "Simplified Chinese", "CONCISE", "DETAILED",
                        "literal separator ` vs `",
                        "only sentence terminator", "untrusted content")
                .doesNotStartWith(" ").doesNotEndWith(" ");
    }

    @Test void missingResourceFailsWithoutLeakingPromptContent() {
        ClassLoader loader = loader(Map.of(
                GOAL_PATH, "goal-secret".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new SystemPromptCatalog(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SYSTEM_PROMPT_MISSING: " + GENERAL_PATH)
                .hasMessageNotContaining("goal-secret");
    }

    @Test void blankResourceFailsWithoutLeakingPromptContent() {
        ClassLoader loader = loader(Map.of(
                GOAL_PATH, "   \r\n".getBytes(StandardCharsets.UTF_8),
                GENERAL_PATH, "general-secret".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new SystemPromptCatalog(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SYSTEM_PROMPT_EMPTY: " + GOAL_PATH)
                .hasMessageNotContaining("general-secret");
    }

    @Test void malformedUtf8FailsWithoutReplacementOrContentLeak() {
        ClassLoader loader = loader(Map.of(
                GOAL_PATH, new byte[] {(byte) 0xC3, (byte) 0x28},
                GENERAL_PATH, "general-secret".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new SystemPromptCatalog(loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SYSTEM_PROMPT_INVALID_UTF8: " + GOAL_PATH)
                .hasMessageNotContaining("general-secret");
    }

    private ClassLoader loader(Map<String, byte[]> resources) {
        return new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                byte[] value = resources.get(name);
                return value == null ? null : new ByteArrayInputStream(value);
            }
        };
    }
}
