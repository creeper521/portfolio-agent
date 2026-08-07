package com.portfolio.agent.evaluation.dataset;

import com.portfolio.agent.evaluation.dataset.GenerationRuleLoader.GenerationRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationRuleLoaderTest {

    private final GenerationRuleLoader loader = new GenerationRuleLoader();

    @Test
    void loadsTheTrackedPublicSubjectSmokeRule() throws Exception {
        Path rulePath = Path.of("..", "governance", "portfolio-governance",
                "evaluation", "generation-rules", "public-subject-smoke.v1.json");

        GenerationRule rule = loader.load(rulePath);

        assertThat(rule.getRuleId()).isEqualTo("public-subject-smoke");
        assertThat(rule.getSelector()).isEqualTo("PUBLIC");
        assertThat(rule.getSubjectTypes()).containsExactly("PROJECT", "CASE");
        assertThat(rule.getTemplate()).isEqualTo("PUBLIC_SUBJECT_SMOKE_V1");
    }

    @Test
    void rejectsUnknownFieldsInRuleDocument() throws Exception {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"),
                "generation-rule-unknown-field.json");
        Files.writeString(temp, """
                {"ruleId":"x","selector":"PUBLIC","subjectTypes":["PROJECT","CASE"],
                 "template":"PUBLIC_SUBJECT_SMOKE_V1","surprise":true}
                """);

        assertThatThrownBy(() -> loader.load(temp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedRuleDocument() throws Exception {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"),
                "generation-rule-malformed.json");
        Files.writeString(temp, "{not-json");

        assertThatThrownBy(() -> loader.load(temp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRuleFile() {
        assertThatThrownBy(() -> loader.load(
                Path.of(System.getProperty("java.io.tmpdir"), "no-such-rule.json")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
