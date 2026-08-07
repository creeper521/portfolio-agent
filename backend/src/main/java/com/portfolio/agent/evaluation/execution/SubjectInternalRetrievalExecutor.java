package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

/**
 * Real subject-internal retrieval layer: resolves the subjects referenced by
 * the eval case (oracle/maintenance, deterministically — no model involved),
 * then retrieves that subject's claims and their approved evidence from the
 * public bundle. The legacy adapter is no longer delegated to.
 */
public final class SubjectInternalRetrievalExecutor implements EvalExecutor {

    private final RuntimeContentSnapshot bundle;

    public SubjectInternalRetrievalExecutor(RuntimeContentSnapshot bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    @Override
    public boolean supports(EvalLayer layer) {
        return layer == EvalLayer.SUBJECT_INTERNAL_RETRIEVAL;
    }

    @Override
    public EvalObservation execute(EvalExecutionInput input, EvalRunContext context) {
        List<EvalSubjectRef> refs = input.getResolvedSubjects();
        if (refs.isEmpty()) {
            refs = inferSubjects(input);
        }
        if (refs.isEmpty()) {
            return error(input, "SUBJECT_UNRESOLVABLE");
        }

        String projectSlug = null;
        String caseSlug = null;
        Set<String> claimIds = new HashSet<>();
        for (EvalSubjectRef ref : refs) {
            String subjectId = resolveSubjectId(ref);
            if (subjectId == null) {
                return error(input, "SUBJECT_UNRESOLVABLE");
            }
            if (ref.getType() == ClaimSubjectType.PROJECT && projectSlug == null) {
                projectSlug = ref.getSlug();
            }
            if (ref.getType() == ClaimSubjectType.CASE && caseSlug == null) {
                caseSlug = ref.getSlug();
            }
            for (Claim claim : bundle.getClaims()) {
                if (claim.getSubjectType() == ref.getType()
                        && subjectId.equals(claim.getSubjectId())) {
                    claimIds.add(claim.getId());
                }
            }
        }
        Set<String> evidenceIds = new HashSet<>();
        for (ClaimEvidenceLink link : bundle.getClaimEvidenceLinks()) {
            if (claimIds.contains(link.getClaimId())) {
                evidenceIds.add(link.getEvidenceId());
            }
        }
        Set<String> approvedEvidence = new HashSet<>();
        for (EvidenceRecord record : bundle.getApprovedEvidence()) {
            approvedEvidence.add(record.getId());
        }
        List<String> approvedEvidenceIds = new ArrayList<>();
        for (String evidenceId : evidenceIds) {
            if (approvedEvidence.contains(evidenceId)) {
                approvedEvidenceIds.add(evidenceId);
            }
        }

        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.PASS,
                projectSlug, caseSlug,
                new ArrayList<>(claimIds), approvedEvidenceIds, List.of(),
                AnswerResolution.ANSWERED, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(), 0L,
                EvalProviderUsage.unavailable(), EvalAnswerShape.empty(), false, false);
    }

    /**
     * Resolves a subject slug to the bundle subject id (claims reference the
     * subject id, not the slug). Returns null when the subject is unknown.
     */
    private String resolveSubjectId(EvalSubjectRef ref) {
        String slug = ref.getSlug();
        if (ref.getType() == ClaimSubjectType.PROJECT) {
            for (com.portfolio.agent.portfolio.domain.ProjectProfile project
                    : bundle.getProjects()) {
                if (project.getSlug().equals(slug)) {
                    return project.getId();
                }
            }
        } else {
            for (com.portfolio.agent.portfolio.domain.CaseStudy caseSubject
                    : bundle.getCases()) {
                if (caseSubject.getSlug().equals(slug)) {
                    return caseSubject.getId();
                }
            }
        }
        return null;
    }

    /** Infer the subject from public query text; maintenance/oracle data is not required. */
    private List<EvalSubjectRef> inferSubjects(EvalExecutionInput input) {
        String query = input.getMessages().stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(com.portfolio.agent.evaluation.domain.EvalMessage::getContent)
                .reduce((first, second) -> second).orElse("")
                .toLowerCase(Locale.ROOT);
        List<EvalSubjectRef> matches = new ArrayList<>();
        for (com.portfolio.agent.portfolio.domain.ProjectProfile project : bundle.getProjects()) {
            if (containsSubject(query, project.getSlug(), project.getTitle(), project.getSummary())) {
                matches.add(new EvalSubjectRef(ClaimSubjectType.PROJECT, project.getSlug()));
            }
        }
        for (com.portfolio.agent.portfolio.domain.CaseStudy subject : bundle.getCases()) {
            if (containsSubject(query, subject.getSlug(), subject.getTitle(), subject.getSummary())) {
                matches.add(new EvalSubjectRef(ClaimSubjectType.CASE, subject.getSlug()));
            }
        }
        return List.copyOf(matches);
    }

    private boolean containsSubject(String query, String slug, String title, String summary) {
        return contains(query, slug) || contains(query, title) || contains(query, summary);
    }

    private boolean contains(String query, String value) {
        return value != null && !value.isBlank()
                && query.contains(value.toLowerCase(Locale.ROOT));
    }

    private EvalObservation error(EvalExecutionInput input, String reasonCode) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.ERROR,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.CAPABILITY_UNAVAILABLE, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(reasonCode), 0L,
                EvalProviderUsage.unavailable(), EvalAnswerShape.empty(), false, false);
    }
}
