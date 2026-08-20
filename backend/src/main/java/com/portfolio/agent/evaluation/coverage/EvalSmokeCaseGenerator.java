package com.portfolio.agent.evaluation.coverage;

import com.portfolio.agent.evaluation.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.ConversationAnswerScope;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates deterministic baseline smoke cases from the reviewed public bundle.
 */
public final class EvalSmokeCaseGenerator {

    private static final List<EvalLayer> SMOKE_LAYERS = List.of(
            EvalLayer.BUNDLE_CONTRACT,
            EvalLayer.HTTP_E2E);
    private static final List<EvalGraderRule> SMOKE_GRADERS = List.of(
            new EvalGraderRule("SUBJECT_MATCH", EvalSeverity.BLOCKING),
            new EvalGraderRule("REFERENCE_INTEGRITY", EvalSeverity.BLOCKING),
            new EvalGraderRule("GROUNDING", EvalSeverity.BLOCKING));

    public List<EvalCase> generate(RuntimeContentSnapshot snapshot) {
        EvalBundleCatalog catalog = new EvalBundleCatalog(snapshot);
        List<EvalCase> generated = new ArrayList<EvalCase>();
        for (EvalBundleCatalog.PublicSubject subject : catalog.getSubjects()) {
            generated.add(createCase(subject));
        }
        generated.sort(Comparator.comparing(EvalCase::getId));
        return List.copyOf(generated);
    }

    private EvalCase createCase(EvalBundleCatalog.PublicSubject subject) {
        EvalSubjectRef subjectRef = subject.toSubjectRef();
        String idPrefix = subject.getType() == ClaimSubjectType.PROJECT
                ? "smoke.project." : "smoke.case.";
        return new EvalCase(
                idPrefix + subject.getSlug(),
                "Public subject smoke: " + subject.getTitle(),
                EvalSplit.CALIBRATION,
                EvalOrigin.BUNDLE_GENERATED,
                EvalRiskLevel.STANDARD,
                "GENERATED",
                "PUBLIC_BUNDLE",
                "PUBLIC_SUBJECT_SMOKE_V1",
                "Every published Project and Case needs a regression smoke check.",
                "BUNDLE_GENERATED",
                List.of("smoke", "public-subject"),
                new EvalCase.Input(List.of(new EvalMessage("user", subject.getTitle()))),
                new EvalCase.Oracle(List.of(subjectRef)),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of(), List.of(), List.of(), List.of()),
                new EvalCase.Execution(SMOKE_LAYERS, 1),
                SMOKE_GRADERS,
                new EvalCase.Maintenance(List.of(subjectRef), true));
    }
}
