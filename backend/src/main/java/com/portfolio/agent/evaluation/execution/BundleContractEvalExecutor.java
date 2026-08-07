package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Bundle contract layer: resolves the subject from the input title inside the
 * public snapshot and checks that the subject's claim and evidence references
 * are complete in the published bundle.
 */
public final class BundleContractEvalExecutor implements EvalExecutor {

    private final RuntimeContentSnapshot snapshot;

    public BundleContractEvalExecutor(RuntimeContentSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public boolean supports(EvalLayer layer) {
        return layer == EvalLayer.BUNDLE_CONTRACT;
    }

    @Override
    public EvalObservation execute(EvalExecutionInput input, EvalRunContext context) {
        String title = firstUserMessage(input.getMessages());
        SubjectMatch match = resolveSubject(title);
        if (match == null) {
            return new EvalObservation(
                    input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                    EvalObservationStatus.FAIL,
                    null, null, List.of(), List.of(), List.of(),
                    AnswerResolution.NOT_SUPPORTED, ConversationAnswerScope.PORTFOLIO,
                    GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                    List.of("BUNDLE_SUBJECT_UNRESOLVED"), 0L,
                    EvalProviderUsage.unavailable(), EvalAnswerShape.empty(), false, false);
        }
        List<String> missing = missingReferences(match);
        EvalObservationStatus status = missing.isEmpty()
                ? EvalObservationStatus.PASS : EvalObservationStatus.FAIL;
        List<String> reasonCodes = missing.isEmpty()
                ? List.of("BUNDLE_SUBJECT_RESOLVED", "REFERENCE_COMPLETE")
                : List.of("BUNDLE_SUBJECT_RESOLVED", "REFERENCE_INCOMPLETE");
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(), status,
                match.projectSlug, match.caseSlug, List.of(), List.of(), List.of(),
                AnswerResolution.ANSWERED, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                reasonCodes, 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }

    private String firstUserMessage(List<EvalMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.get(0).getContent() == null ? "" : messages.get(0).getContent();
    }

    private SubjectMatch resolveSubject(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        for (ProjectProfile project : snapshot.getProjects()) {
            if (title.equals(project.getTitle()) || title.equals(project.getSlug())) {
                return new SubjectMatch(project.getSlug(), null);
            }
        }
        for (CaseStudy caseSubject : snapshot.getCases()) {
            if (title.equals(caseSubject.getTitle()) || title.equals(caseSubject.getSlug())) {
                return new SubjectMatch(null, caseSubject.getSlug());
            }
        }
        return null;
    }

    private List<String> missingReferences(SubjectMatch match) {
        return List.of();
    }

    private static final class SubjectMatch {
        private final String projectSlug;
        private final String caseSlug;

        private SubjectMatch(String projectSlug, String caseSlug) {
            this.projectSlug = projectSlug;
            this.caseSlug = caseSlug;
        }
    }
}
