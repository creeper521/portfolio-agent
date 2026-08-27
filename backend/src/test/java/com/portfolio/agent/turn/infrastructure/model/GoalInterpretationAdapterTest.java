package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelResponse;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalInterpretationAdapterTest {
    @Test void noneSelectionDoesNotCallGoalProvider() {
        AtomicInteger calls = new AtomicInteger();
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(
                StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("NONE must not call provider");
                }), new ObjectMapper(), new GoalProposalCodec(), "system", 100,
                Duration.ofSeconds(2));

        assertThatThrownBy(() -> adapter.interpret(
                input(),
                com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(3), Clock.systemUTC()),
                com.portfolio.agent.infrastructure.model
                        .ResolvedModelExecution.none()))
                .isInstanceOf(com.portfolio.agent.turn.planning
                        .GoalInterpretationUnavailableException.class);
        assertThat(calls).hasValue(0);
    }

    @Test void reportsGoalCodecRejectionAsSchemaWithoutProviderBody() {
        List<DiagnosticEvent> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String providerBody = "{\"privateProviderBody\":\"sentinel\"}";
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(
                StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse(providerBody);
                }),
                new ObjectMapper(), new GoalProposalCodec(), "system", 100,
                Duration.ofSeconds(2), new ModelOutputDiagnostics(events::add));

        SelectedModelFailureException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> adapter.interpret(input(),
                com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(3), Clock.systemUTC()),
                ModelExecutionSnapshotFixture.model()),
                        SelectedModelFailureException.class);
        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields().get("provider.operation"))
                    .isEqualTo("GOAL_INTERPRETATION");
            assertThat(event.getFields().get("failure.layer"))
                    .isEqualTo("PROVIDER_DRAFT_SCHEMA");
            assertThat(event.getFields().get("failure.reason"))
                    .isEqualTo("UNKNOWN_FIELD");
            assertThat(event.toString()).doesNotContain(providerBody, "sentinel");
        });
        assertThat(calls).as("schema rejection must not trigger repair").hasValue(1);
    }

    @Test void reportsClosedReasonForMissingBlockedGoalWithoutProviderBody() {
        List<DiagnosticEvent> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String providerBody = """
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"NEEDS_CLARIFICATION",
                  "candidateKey":null,
                  "recentReference":null,
                  "goal":null,
                  "clarification":{
                    "field":"SUBJECT",
                    "prompt":"请选择要了解的公开项目",
                    "blockedGoal":null
                  }
                }
                """;
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(
                StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse(providerBody);
                }),
                new ObjectMapper(), new GoalProposalCodec(), "system", 100,
                Duration.ofSeconds(2), new ModelOutputDiagnostics(events::add));

        SelectedModelFailureException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> adapter.interpret(input(),
                                com.portfolio.agent.turn.execution.TurnDeadline.after(
                                        Duration.ofSeconds(3), Clock.systemUTC()),
                                ModelExecutionSnapshotFixture.model()),
                        SelectedModelFailureException.class);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields().get("failure.layer"))
                    .isEqualTo("PROVIDER_DRAFT_SCHEMA");
            assertThat(event.getFields().get("failure.code"))
                    .isEqualTo("OUTPUT_SCHEMA_REJECTED");
            assertThat(event.getFields().get("failure.reason"))
                    .isEqualTo("CLARIFICATION_BLOCKED_GOAL_REQUIRED");
            assertThat(event.toString()).doesNotContain(
                    providerBody, "请选择要了解的公开项目");
        });
        assertThat(calls).as("closed rejection must not trigger repair").hasValue(1);
    }

    @Test void reportsClosedReasonForUnsupportedRootKindWithoutProviderBody() {
        List<DiagnosticEvent> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String providerBody = "{\"kind\":\"PORTFOLIO_RECOMMEND\",\"private\":\"sentinel\"}";
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(
                StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse(providerBody);
                }),
                new ObjectMapper(), new GoalProposalCodec(), "system", 100,
                Duration.ofSeconds(2), new ModelOutputDiagnostics(events::add));

        SelectedModelFailureException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> adapter.interpret(input(),
                                com.portfolio.agent.turn.execution.TurnDeadline.after(
                                        Duration.ofSeconds(3), Clock.systemUTC()),
                                ModelExecutionSnapshotFixture.model()),
                        SelectedModelFailureException.class);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields().get("failure.layer"))
                    .isEqualTo("PROVIDER_DRAFT_SCHEMA");
            assertThat(event.getFields().get("failure.code"))
                    .isEqualTo("OUTPUT_SCHEMA_REJECTED");
            assertThat(event.getFields().get("failure.reason"))
                    .isEqualTo("UNSUPPORTED_ROOT_KIND");
            assertThat(event.toString()).doesNotContain(providerBody, "sentinel");
        });
        assertThat(calls).as("closed rejection must not trigger repair").hasValue(1);
    }

    @Test void reportsProviderDraftSchemaRejectionWithoutRepairOrRetry() {
        List<DiagnosticEvent> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(
                StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse("""
                            {
                              "decision":"STANDARD_GOAL",
                              "goal":{
                                "goalKind":"PORTFOLIO_RECOMMEND",
                                "inputText":"解释幂等",
                                "constraints":[]
                              }
                            }
                            """);
                }),
                new ObjectMapper(), new GoalProposalCodec(), "system", 100,
                Duration.ofSeconds(2), new ModelOutputDiagnostics(events::add));

        SelectedModelFailureException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> adapter.interpret(input(),
                                com.portfolio.agent.turn.execution.TurnDeadline.after(
                                        Duration.ofSeconds(3), Clock.systemUTC()),
                                ModelExecutionSnapshotFixture.model()),
                        SelectedModelFailureException.class);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields().get("failure.layer"))
                    .isEqualTo("PROVIDER_DRAFT_SCHEMA");
            assertThat(event.getFields().get("failure.code"))
                    .isEqualTo("OUTPUT_SCHEMA_REJECTED");
            assertThat(event.getFields().get("failure.reason"))
                    .isEqualTo("FIELD_VALUE_INVALID");
        });
        assertThat(calls).as("schema rejection must not trigger repair")
                .hasValue(1);
    }

    @Test void reportsDeterministicCompilerRejectionWithoutRepairOrRetry() {
        List<DiagnosticEvent> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(
                StructuredModelTestFixtures.gateway((binding, request) -> {
                    calls.incrementAndGet();
                    return new StructuredModelResponse("""
                            {
                              "decision":"STANDARD_GOAL",
                              "goal":{
                                "goalKind":"PORTFOLIO_RECOMMEND",
                                "inputText":"解释幂等",
                                "requestedSize":3,
                                "constraints":["CAPABILITY_FRONTEND"]
                              }
                            }
                            """);
                }),
                new ObjectMapper(), new GoalProposalCodec(), "system", 100,
                Duration.ofSeconds(2), new ModelOutputDiagnostics(events::add));

        SelectedModelFailureException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> adapter.interpret(input(),
                                com.portfolio.agent.turn.execution.TurnDeadline.after(
                                        Duration.ofSeconds(3), Clock.systemUTC()),
                                ModelExecutionSnapshotFixture.model()),
                        SelectedModelFailureException.class);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields().get("failure.layer"))
                    .isEqualTo("DETERMINISTIC_COMPILER");
            assertThat(event.getFields().get("failure.code"))
                    .isEqualTo("OUTPUT_COMPILER_REJECTED");
            assertThat(event.getFields().get("failure.reason"))
                    .isEqualTo("DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE");
        });
        assertThat(calls).as("compiler rejection must not trigger repair")
                .hasValue(1);
    }

    @Test void sendsOnlyGoalLevelAuthorityAndDecodesStrictProposal() {
        AtomicReference<StructuredModelRequest> captured = new AtomicReference<>();
        String systemPrompt = "goal-system-prompt";
        GoalInterpretationAdapter adapter = new GoalInterpretationAdapter(
                StructuredModelTestFixtures.gateway((binding, request) -> {
            captured.set(request);
            return new StructuredModelResponse("""
                    {
                      "decision":"STANDARD_GOAL",
                      "goal":{
                        "goalKind":"GENERAL_EXPLANATION",
                        "inputText":"解释幂等",
                        "topicText":"幂等",
                        "depth":"STANDARD"
                      }
                    }
                    """);
        }), new ObjectMapper(), new GoalProposalCodec(), systemPrompt, 1200,
                Duration.ofSeconds(2));

        GoalInterpretationResult result = adapter.interpret(
                input(), com.portfolio.agent.turn.execution.TurnDeadline.after(
                        Duration.ofSeconds(3), Clock.systemUTC()),
                ModelExecutionSnapshotFixture.model());
        assertThat(result.getKind())
                .isEqualTo(GoalInterpretationResult.Kind.SEMANTIC_ROUTE);
        assertThat(captured.get().systemPrompt()).isEqualTo(systemPrompt);
        assertThat(captured.get().userPrompt()).contains(
                        "interpretationMode", "discussionState",
                        "allowedRoutes", "routeCandidates",
                        "allowedGoalKinds", "publicSubjects",
                        "allowedRecommendationConstraints", "CAPABILITY_SQL",
                        "defaultSubject", "audienceProfile",
                        "recentSemanticState")
                .doesNotContain("\"schema\"")
                .doesNotContain("semantic-route-proposal-v1")
                .doesNotContain("taskType", "dependencies");
        assertThat(captured.get().maxOutputTokens()).isEqualTo(1200);
        assertThat(captured.get().temperature()).isZero();
    }

    private GoalInterpretationInput input() {
        return new GoalInterpretationInput(
                "解释幂等", List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE,
                null, List.of(), Set.of(
                        com.portfolio.agent.turn.planning.SemanticRouteProposal.Route.STANDARD_GOAL,
                        com.portfolio.agent.turn.planning.SemanticRouteProposal.Route.NEEDS_CLARIFICATION),
                Set.of("CAPABILITY_SQL"));
    }
}
