package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvalRunPlannerTest {

    private final EvalRunPlanner planner = new EvalRunPlanner();

    @Test
    void validateModeDoesNotScheduleExecutorCalls() {
        EvalRunPlan plan = planner.plan(EvalRunMode.VALIDATE, cases(), Set.of());

        assertThat(plan.getPlannedCases()).isEmpty();
        assertThat(plan.isOfflinePassRequired()).isFalse();
        assertThat(plan.isBlocking()).isTrue();
    }

    @Test
    void providerModeRequiresOfflineIdentityAndSelectsOnlyProviderEligibleCases() {
        EvalRunPlan plan = planner.plan(EvalRunMode.PROVIDER, cases(), Set.of());

        assertThat(plan.isOfflinePassRequired()).isTrue();
        assertThat(plan.isBlocking()).isTrue();
        assertThat(plan.getPlannedCases()).extracting(EvalCase::getId)
                .containsExactly("answer.role-reset.001", "regression.provider-timeout.001",
                        "shallow.high.changed.001");
        assertThat(plan.getPlannedCases()).allMatch(item -> item.getProviderTrials() == 3);
        assertThat(plan.getPlannedCases()).allMatch(item ->
                item.getRiskLevel() == EvalRiskLevel.HIGH
                        || item.getRiskLevel() == EvalRiskLevel.INVARIANT);
    }

    @Test
    void offlineModeIncludesAllTrackedCasesExceptExternalChallenge() {
        EvalRunPlan plan = planner.plan(EvalRunMode.OFFLINE, cases(), Set.of());

        assertThat(plan.getPlannedCases()).extracting(EvalCase::getId)
                .containsExactly("answer.role-reset.001", "provider-only.001",
                        "regression.provider-timeout.001", "shallow.changed.001",
                        "shallow.high.changed.001", "smoke.project.demo")
                .doesNotContain("challenge.private.001");
        assertThat(plan.isOfflinePassRequired()).isFalse();
    }

    @Test
    void changedSubjectForcesAnAuthoredDeepCaseIntoOfflineAndProviderPlans() {
        EvalSubjectRef changed = new EvalSubjectRef(ClaimSubjectType.CASE, "case-role-reset");

        EvalRunPlan offline = planner.plan(EvalRunMode.OFFLINE, cases(), Set.of(changed));
        EvalRunPlan provider = planner.plan(EvalRunMode.PROVIDER, cases(), Set.of(changed));

        assertThat(offline.getPlannedCases()).extracting(EvalCase::getId)
                .contains("answer.role-reset.001");
        assertThat(provider.getPlannedCases()).extracting(EvalCase::getId)
                .contains("answer.role-reset.001");
    }

    @Test
    void changedSubjectDoesNotPromoteShallowStandardBundleCaseToProvider() {
        EvalSubjectRef changed = new EvalSubjectRef(ClaimSubjectType.CASE, "case-role-reset");

        EvalRunPlan provider = planner.plan(EvalRunMode.PROVIDER, cases(), Set.of(changed));

        assertThat(provider.getPlannedCases()).extracting(EvalCase::getId)
                .contains("answer.role-reset.001")
                .doesNotContain("shallow.changed.001", "shallow.high.changed.001");
    }

    @Test
    void periodicModeUsesTheProviderSampleButDoesNotBlockRelease() {
        EvalRunPlan periodic = planner.plan(EvalRunMode.PERIODIC, cases(), Set.of());
        EvalRunPlan provider = planner.plan(EvalRunMode.PROVIDER, cases(), Set.of());

        assertThat(periodic.getPlannedCases()).extracting(EvalCase::getId)
                .containsExactlyElementsOf(provider.getPlannedCases().stream()
                        .map(EvalCase::getId).toList());
        assertThat(periodic.isBlocking()).isFalse();
        assertThat(periodic.isOfflinePassRequired()).isTrue();
    }

    private List<EvalCase> cases() {
        return List.of(
                evalCase("smoke.project.demo", EvalSplit.CALIBRATION, EvalRiskLevel.STANDARD,
                        0, true, "project-demo", List.of(EvalLayer.BUNDLE_CONTRACT)),
                evalCase("answer.role-reset.001", EvalSplit.HOLDOUT, EvalRiskLevel.HIGH,
                        3, false, "case-role-reset", List.of(EvalLayer.INTELLIGENCE)),
                evalCase("regression.provider-timeout.001", EvalSplit.REGRESSION,
                        EvalRiskLevel.INVARIANT, 3, false, "project-demo",
                        List.of(EvalLayer.HTTP_E2E)),
                evalCase("provider-only.001", EvalSplit.HOLDOUT, EvalRiskLevel.STANDARD,
                        3, false, "project-demo", List.of(EvalLayer.HTTP_E2E)),
                evalCase("shallow.changed.001", EvalSplit.HOLDOUT, EvalRiskLevel.STANDARD,
                        3, false, "case-role-reset", List.of(EvalLayer.BUNDLE_CONTRACT)),
                evalCase("shallow.high.changed.001", EvalSplit.HOLDOUT, EvalRiskLevel.HIGH,
                        3, false, "case-role-reset", List.of(EvalLayer.BUNDLE_CONTRACT)),
                evalCase("challenge.private.001", EvalSplit.CHALLENGE, EvalRiskLevel.INVARIANT,
                        3, false, "project-demo", List.of(EvalLayer.INTELLIGENCE)));
    }

    private EvalCase evalCase(String id, EvalSplit split, EvalRiskLevel riskLevel,
                              int providerTrials, boolean generated, String subjectSlug,
                              List<EvalLayer> layers) {
        EvalSubjectRef subject = new EvalSubjectRef(ClaimSubjectType.CASE, subjectSlug);
        return new EvalCase(id, id, split, EvalOrigin.HUMAN_AUTHORED, riskLevel, "APPROVED",
                "reviewer", "TEST", "test", "2026-08-04.1", List.of("test"),
                new EvalCase.Input(List.of(new EvalMessage("user", "Test question"))),
                new EvalCase.Oracle(List.of(subject)),
                new EvalCase.Expectations(List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO), List.of(), List.of(), List.of(),
                        List.of()),
                new EvalCase.Execution(layers, providerTrials),
                List.of(new EvalGraderRule("SUBJECT_MATCH", EvalSeverity.BLOCKING)),
                new EvalCase.Maintenance(List.of(subject), generated));
    }
}
