package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralDraftValidatorTest {
    private final GeneralDraftCodec codec = new GeneralDraftCodec(new ObjectMapper());
    private final GeneralDraftValidator validator = new GeneralDraftValidator();

    @Test void validExplanationBecomesMinimalSemanticResult() {
        GeneralSemanticResult result = validator.validate(
                GeneralTestFixtures.explanation(), codec.decode(GeneralTestFixtures.VALID_EXPLANATION));
        assertThat(result.getStatements()).extracting(GeneralSemanticResult.Statement::getRole)
                .containsExactly(GeneralSemanticResult.Role.DEFINITION, GeneralSemanticResult.Role.MECHANISM);
        assertThat(result.getContentVersion()).isEqualTo("public-1");
    }

    @Test void missingMechanismFailsInsteadOfBecomingPartial() {
        String incomplete = GeneralTestFixtures.VALID_EXPLANATION.replace(
                ",\n  {\"role\":\"MECHANISM\",\"text\":\"它通过有界调度与状态隔离控制竞争。\"}", "");
        assertThatThrownBy(() -> validator.validate(
                GeneralTestFixtures.explanation(), codec.decode(incomplete)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateDefinitionFailsClosed() {
        assertRejected("""
                {"role":"DEFINITION","text":"定义一。"},
                {"role":"DEFINITION","text":"定义二。"},
                {"role":"MECHANISM","text":"机制。"}
                """);
    }

    @Test void duplicateMechanismFailsClosed() {
        assertRejected("""
                {"role":"DEFINITION","text":"定义。"},
                {"role":"MECHANISM","text":"机制一。"},
                {"role":"MECHANISM","text":"机制二。"}
                """);
    }

    @Test void comparisonRoleInsideExplanationFailsClosed() {
        assertRejected("""
                {"role":"DEFINITION","text":"定义。"},
                {"role":"COMPARISON","text":"比较。","subject":"并发控制","dimension":"机制"},
                {"role":"MECHANISM","text":"机制。"}
                """);
    }

    @Test void mechanismBeforeDefinitionFailsClosed() {
        assertRejected("""
                {"role":"MECHANISM","text":"机制。"},
                {"role":"DEFINITION","text":"定义。"}
                """);
    }

    private void assertRejected(String statements) {
        String json = """
                {"topic":"并发控制","statements":[%s],"caveats":[]}
                """.formatted(statements);
        assertThatThrownBy(() -> validator.validate(
                GeneralTestFixtures.explanation(), codec.decode(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explanation roles");
    }
}
