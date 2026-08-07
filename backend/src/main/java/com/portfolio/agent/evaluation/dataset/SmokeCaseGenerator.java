package com.portfolio.agent.evaluation.dataset;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.evaluation.dataset.GenerationRuleLoader.GenerationRule;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic expansion of the public-subject-smoke generation rule against
 * the current public bundle. Every eligible public subject yields exactly one
 * smoke case with a stable id derived from subject type and slug. Output is
 * fully deterministic: same bundle, same rule, same case list.
 */
public final class SmokeCaseGenerator {

    public static final String RULE_TEMPLATE = "PUBLIC_SUBJECT_SMOKE_V1";

    public List<EvalCase> generate(
            RuntimeContentSnapshot bundle,
            GenerationRule rule) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(rule, "rule");
        validateRule(rule);
        List<EvalCase> cases = new ArrayList<>();
        for (ProjectProfile project : bundle.getProjects()) {
            cases.add(smokeCase(
                    project.getSlug(), project.getTitle(),
                    ClaimSubjectType.PROJECT, bundle));
        }
        for (CaseStudy caseSubject : bundle.getCases()) {
            cases.add(smokeCase(
                    caseSubject.getSlug(), caseSubject.getTitle(),
                    ClaimSubjectType.CASE, bundle));
        }
        cases.sort(Comparator
                .comparing(EvalCase::getId));
        return List.copyOf(cases);
    }

    private void validateRule(GenerationRule rule) {
        if (!"PUBLIC".equals(rule.getSelector())) {
            throw new IllegalArgumentException(
                    "Invalid generation rule: selector must be PUBLIC");
        }
        if (rule.getSubjectTypes() == null
                || rule.getSubjectTypes().isEmpty()
                || !rule.getSubjectTypes().containsAll(List.of("PROJECT", "CASE"))) {
            throw new IllegalArgumentException(
                    "Invalid generation rule: subjectTypes must cover PROJECT and CASE");
        }
        if (!RULE_TEMPLATE.equals(rule.getTemplate())) {
            throw new IllegalArgumentException(
                    "Invalid generation rule: unsupported template " + rule.getTemplate());
        }
    }

    private EvalCase smokeCase(
            String slug,
            String title,
            ClaimSubjectType type,
            RuntimeContentSnapshot bundle) {
        String typeName = type == ClaimSubjectType.PROJECT ? "project" : "case";
        String id = "smoke." + typeName + "." + slug;
        EvalSubjectRef subject = new EvalSubjectRef(type, slug);
        return new EvalCase(
                id, title, EvalSplit.CALIBRATION, EvalOrigin.BUNDLE_GENERATED,
                EvalRiskLevel.STANDARD, "APPROVED", "phase-0-generator",
                "PUBLIC_BUNDLE", "公开主体冒烟（自动生成）",
                bundle.getContentVersion(),
                List.of("smoke", "generated"),
                new EvalCase.Input(List.of(new EvalMessage("user", title))),
                new EvalCase.Oracle(List.of(subject)),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()),
                new EvalCase.Execution(List.of(EvalLayer.BUNDLE_CONTRACT), 3),
                List.of(
                        new EvalGraderRule("SUBJECT_MATCH", EvalSeverity.BLOCKING),
                        new EvalGraderRule("RESOLUTION", EvalSeverity.BLOCKING),
                        new EvalGraderRule("ANSWER_QUALITY", EvalSeverity.SCORED)),
                new EvalCase.Maintenance(List.of(subject), true));
    }
}
