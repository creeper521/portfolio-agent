package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.turn.infrastructure.model.GeneralProviderDraftCompiler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralModelOutputDiagnosticsTest {

    @Test
    void retrySchedulingPublishesOnlyBoundedAttemptFailureAndWaitMetadata() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ModelOutputDiagnostics diagnostics = new ModelOutputDiagnostics(events::add);

        diagnostics.retryScheduled(
                2, 2, "PROVIDER_UNAVAILABLE", "NO_WAIT");
        diagnostics.retryScheduled(
                1, 2, "PRIVATE_FAILURE_TEXT", "arbitrary wait text");

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("provider.call.retry_scheduled");
            assertThat(event.getFields())
                    .containsEntry("attempt.index", 2)
                    .containsEntry("attempt.count", 2)
                    .containsEntry("failure.code", "PROVIDER_UNAVAILABLE")
                    .containsEntry("wait.bucket", "NO_WAIT")
                    .doesNotContainKeys(
                            "provider.operation", "provider.attempt_id", "response.body");
        });
    }

    @Test
    void retrySchedulingRejectsImpossibleFailureAndWaitCombinations() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ModelOutputDiagnostics diagnostics =
                new ModelOutputDiagnostics(events::add);

        diagnostics.retryScheduled(
                2, 2, "DEADLINE_EXCEEDED", "NO_WAIT");
        diagnostics.retryScheduled(
                2, 2, "TRANSPORT_UNAVAILABLE", "NO_WAIT");
        diagnostics.retryScheduled(
                2, 2, "PROVIDER_UNAVAILABLE", "NO_WAIT");
        diagnostics.retryScheduled(
                2, 2, "RATE_LIMITED", "JITTER_100_250_MS");
        diagnostics.retryScheduled(
                2, 2, "RATE_LIMITED", "RETRY_AFTER_LE_1S");

        diagnostics.retryScheduled(
                2, 2, "DEADLINE_EXCEEDED", "JITTER_100_250_MS");
        diagnostics.retryScheduled(
                2, 2, "TRANSPORT_UNAVAILABLE", "RETRY_AFTER_LE_1S");
        diagnostics.retryScheduled(
                2, 2, "PROVIDER_UNAVAILABLE", "JITTER_100_250_MS");
        diagnostics.retryScheduled(
                2, 2, "RATE_LIMITED", "NO_WAIT");

        assertThat(events).hasSize(5);
        assertThat(events).allSatisfy(event -> {
            String failure = (String) event.getFields().get("failure.code");
            String wait = (String) event.getFields().get("wait.bucket");
            if (failure.equals("RATE_LIMITED")) {
                assertThat(wait).isIn(
                        "JITTER_100_250_MS", "RETRY_AFTER_LE_1S");
            } else {
                assertThat(wait).isEqualTo("NO_WAIT");
            }
        });
    }

    @Test
    void admissionPublishesOnlyClosedLevelRuleAndCount() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        ModelOutputDiagnostics diagnostics = new ModelOutputDiagnostics(events::add);
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                GeneralTestFixtures.explanation(), diagnostics);

        compiler.compile(new ObjectMapper().readTree("""
                {"definition":"  定义  ","mechanism":"机制。",
                 "caveats":null,"providerEcho":"private-provider-value"}
                """));

        assertThat(events).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getName()).isEqualTo("provider.output.admitted");
            assertThat(event.getFields()).containsEntry(
                    "provider.operation", "GENERAL_KNOWLEDGE");
            assertThat(event.toString())
                    .doesNotContain("providerEcho", "private-provider-value", "定义", "机制");
        });
        assertThat(events).anySatisfy(event -> assertThat(event.getFields())
                .containsEntry("admission.level", "DEGRADED")
                .containsEntry("normalization.rule", "UNKNOWN_FIELD_COUNT")
                .containsEntry("normalization.count", 1));
        assertThat(events).anySatisfy(event -> assertThat(event.getFields())
                .containsEntry("normalization.rule", "MISSING_CAVEATS_AS_EMPTY"));
    }

    @Test
    void admissionDiagnosticsPreserveTheFrozenFirstAppliedRuleOrder() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                GeneralTestFixtures.explanation(),
                new ModelOutputDiagnostics(events::add));

        compiler.compile(new ObjectMapper().readTree("""
                {"unknown":true,
                 "definition":"  Café\\t是术语名称  ",
                 "mechanism":["机制一","机制二"]}
                """));

        assertThat(events).extracting(event ->
                        event.getFields().get("normalization.rule"))
                .containsExactly(
                        "UNKNOWN_FIELD_COUNT",
                        "WRAP_STRING_AS_ARRAY",
                        "UNICODE_NORMALIZE_NFC",
                        "TRIM_TEXT",
                        "COLLAPSE_MEANINGLESS_WHITESPACE",
                        "NORMALIZE_TERMINAL_PUNCTUATION",
                        "JOIN_ROLE_SENTENCES",
                        "MISSING_CAVEATS_AS_EMPTY");
    }

    @Test
    void codecAndValidatorRejectionsHaveDistinctSafeLayers() {
        assertLayer("{}", "SCHEMA", "OUTPUT_SCHEMA_REJECTED");
        assertLayer(GeneralTestFixtures.VALID_EXPLANATION.replace(
                "\"topic\":\"并发控制\"", "\"topic\":\"其他主题\""),
                "SEMANTIC", "OUTPUT_SEMANTIC_REJECTED");
    }

    private void assertLayer(String output, String layer, String code) {
        List<DiagnosticEvent> events = new ArrayList<>();
        GeneralKnowledgeGenerator generator = new GeneralKnowledgeGenerator(
                (request, modelExecution) -> layer.equals("SCHEMA")
                        ? StructuredModelTestFixtures.contracts().validate(
                                new StructuredContractRef(
                                        ModelOperation.TURN_INTERPRETATION,
                                        "goal.proposal.v5"),
                                "{\"kind\":\"CONVERSATIONAL\","
                                        + "\"message\":\"请说明目标\"}")
                        : StructuredModelTestFixtures.validatedGeneral(output),
                new GeneralDraftCodec(new ObjectMapper()),
                new GeneralDraftValidator(),
                new ModelOutputDiagnostics(events::add));

        assertThatThrownBy(() -> generator.generate(GeneralTestFixtures.explanation()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("provider.output.rejected");
            assertThat(event.getFields().get("provider.operation"))
                    .isEqualTo("GENERAL_KNOWLEDGE");
            assertThat(event.getFields().get("failure.layer")).isEqualTo(layer);
            assertThat(event.getFields().get("failure.code")).isEqualTo(code);
            if (layer.equals("SEMANTIC")) {
                assertThat(event.getFields().get("failure.reason"))
                        .isEqualTo("TOPIC_MISMATCH");
            } else {
                assertThat(event.getFields()).doesNotContainKey("failure.reason");
            }
            assertThat(event.toString()).doesNotContain(output);
        });
    }
}
