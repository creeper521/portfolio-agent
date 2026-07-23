package com.portfolio.agent.portfolio.validation;

import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.ClaimVerificationStatus;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.ReviewStatus;
import com.portfolio.agent.portfolio.domain.TimelineEvent;
import com.portfolio.agent.portfolio.domain.SupportType;
import com.portfolio.agent.portfolio.domain.VerificationBasis;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class PortfolioSnapshotValidator {

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("2.0", "3.0");
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9-]{1,64}");

    public void validate(PortfolioSnapshot snapshot) {
        require(snapshot != null, "snapshot is required");
        require(snapshot.getSchemaVersion() != null
                        && SUPPORTED_SCHEMA_VERSIONS.contains(snapshot.getSchemaVersion()),
                "unsupported schemaVersion: " + snapshot.getSchemaVersion());
        require(hasText(snapshot.getContentVersion()), "contentVersion is required");
        require(snapshot.getPublishedAt() != null, "publishedAt is required");
        require(snapshot.getOwner() != null, "owner is required");
        require(hasText(snapshot.getOwner().getRole()), "owner role is required");
        require(hasText(snapshot.getOwner().getSummary()), "owner summary is required");

        List<ProjectProfile> projects = requiredList(snapshot.getProjects(), "projects");
        List<CaseStudy> cases = requiredList(snapshot.getCases(), "cases");
        List<Claim> claims = requiredList(snapshot.getClaims(), "claims");
        List<ClaimEvidenceLink> links = requiredList(
                snapshot.getClaimEvidenceLinks(), "claimEvidenceLinks");
        List<QuestionDefinition> questions = requiredList(snapshot.getQuestions(), "questions");
        List<EvidenceRecord> evidence = requiredList(snapshot.getEvidence(), "evidence");
        List<TimelineEvent> timeline = requiredList(snapshot.getTimeline(), "timeline");

        Map<String, ProjectProfile> projectsById = uniqueById(projects, ProjectProfile::getId, "project");
        Map<String, ProjectProfile> projectsBySlug = uniqueById(projects, ProjectProfile::getSlug,
                "project slug");
        Map<String, ProjectProfile> projectsByCode =
                uniqueById(projects, ProjectProfile::getCode, "project code");
        Map<String, CaseStudy> casesById = uniqueById(cases, CaseStudy::getId, "case");
        Map<String, CaseStudy> casesBySlug =
                uniqueById(cases, CaseStudy::getSlug, "case slug");
        Map<String, CaseStudy> casesByCode =
                uniqueById(cases, CaseStudy::getCode, "case code");
        requireDisjoint(projectsById.keySet(), casesById.keySet(),
                "project and case ids must be disjoint");
        requireDisjoint(projectsByCode.keySet(), casesByCode.keySet(),
                "project and case codes must be disjoint");
        requireDisjoint(projectsBySlug.keySet(), casesBySlug.keySet(),
                "project and case slugs must be disjoint");
        Map<String, Claim> claimsById = uniqueById(claims, Claim::getId, "claim");
        Map<String, QuestionDefinition> questionsById = uniqueById(questions,
                QuestionDefinition::getId, "question");
        Map<String, EvidenceRecord> evidenceById = uniqueById(evidence, EvidenceRecord::getId,
                "evidence");
        uniqueById(links, ClaimEvidenceLink::getId, "claim evidence link");
        Map<String, TimelineEvent> timelineById = uniqueById(
                timeline, TimelineEvent::getId, "timeline");

        require(!projectsBySlug.isEmpty(), "at least one project is required");

        Map<String, List<ClaimEvidenceLink>> linksByClaimId = links.stream()
                .collect(java.util.stream.Collectors.groupingBy(ClaimEvidenceLink::getClaimId));
        for (ClaimEvidenceLink link : links) {
            require(claimsById.containsKey(link.getClaimId()),
                    "claim evidence link reference does not exist: " + link.getClaimId());
            require(evidenceById.containsKey(link.getEvidenceId()),
                    "claim evidence link reference does not exist: " + link.getEvidenceId());
            require(link.getSupportType() != null,
                    "claim evidence link supportType is required: " + link.getId());
            require(hasText(link.getScope()),
                    "claim evidence link scope is required: " + link.getId());
            require(link.getReviewStatus() == ReviewStatus.APPROVED,
                    "claim evidence link must be APPROVED: " + link.getId());
        }

        for (Claim claim : claims) {
            require(claim.getSubjectType() != null, "claim subjectType is required: " + claim.getId());
            require(hasText(claim.getSubjectId()), "claim subjectId is required: " + claim.getId());
            if (claim.getSubjectType() == ClaimSubjectType.PROJECT) {
                require(projectsById.containsKey(claim.getSubjectId()),
                        "claim subject reference does not exist: " + claim.getSubjectId());
            } else if (claim.getSubjectType() == ClaimSubjectType.CASE) {
                require(casesById.containsKey(claim.getSubjectId()),
                        "claim subject reference does not exist: " + claim.getSubjectId());
            }
            require(claim.getCategory() != null, "claim category is required: " + claim.getId());
            require(hasText(claim.getStatement()), "claim statement is required: " + claim.getId());
            require(claim.getAchievementStatus() != null,
                    "claim achievementStatus is required: " + claim.getId());
            require(claim.getVerificationBasis() != null,
                    "claim verificationBasis is required: " + claim.getId());
            require(claim.getVerificationStatus() != null,
                    "claim verificationStatus is required: " + claim.getId());
            require(claim.getMateriality() != null, "claim materiality is required: " + claim.getId());
            requiredNonBlankList(claim.getTopics(), "claim topics");
            validateVerification(claim, linksByClaimId.getOrDefault(claim.getId(), List.of()));
        }

        for (QuestionDefinition question : questions) {
            requireAssociation(question.getProjectIds(), question.getCaseIds(), "question");
            for (String projectId : question.getProjectIds()) {
                require(projectsById.containsKey(projectId),
                        "question project reference does not exist: " + projectId);
            }
            for (String caseId : question.getCaseIds()) {
                require(casesById.containsKey(caseId),
                        "question case reference does not exist: " + caseId);
            }
            require(hasText(question.getText()), "question text is required");
            requiredNonBlankList(question.getAliases(), "question aliases");
            requiredNonBlankList(question.getAudiences(), "question audiences");
            requiredNonBlankList(question.getTopics(), "question topics");
            require(!question.getPreferredClaimCategories().isEmpty(),
                    "question preferredClaimCategories must not be empty");
            requiredNonBlankList(question.getPlacements(), "question placements");
            require(question.isDeterministicEntry(),
                    "published question must be a deterministic entry");
        }

        for (EvidenceRecord item : evidence) {
            require(hasText(item.getCode()), "evidence code is required: " + item.getId());
            require(hasText(item.getTitle()), "evidence title is required: " + item.getId());
            require(item.getType() != null, "evidence type is required: " + item.getId());
            require(item.getPeriodStart() != null && item.getPeriodEnd() != null,
                    "evidence period is required: " + item.getId());
            require(!item.getPeriodEnd().isBefore(item.getPeriodStart()),
                    "evidence period is invalid: " + item.getId());
            require(item.getPublicStatus() == EvidenceStatus.APPROVED,
                    "evidence must be APPROVED: " + item.getId());
            require(item.getRawContentPublic() != null,
                    "evidence rawContentPublic is required: " + item.getId());
            require(!item.getRawContentPublic(),
                    "evidence raw content must not be public: " + item.getId());
            require(item.getSourceCount() > 0,
                    "evidence sourceCount must be positive: " + item.getId());
            require(hasText(item.getSummary()), "evidence summary is required: " + item.getId());
        }

        for (ProjectProfile project : projects) {
            require(hasText(project.getCode()), "project code is required: " + project.getId());
            require(hasText(project.getSlug()), "project slug is required");
            require(SLUG_PATTERN.matcher(project.getSlug()).matches(),
                    "project slug format is invalid: " + project.getSlug());
            require(hasText(project.getTitle()), "project title is required: " + project.getId());
            require(hasText(project.getSummary()), "project summary is required: " + project.getId());
            require(hasText(project.getBackground()),
                    "project background is required: " + project.getId());
            requiredNonBlankList(project.getResponsibilities(), "project responsibilities");
            require(hasText(project.getSolution()), "project solution is required: " + project.getId());
            requiredNonBlankList(project.getKeyDecisions(), "project keyDecisions");
            requiredNonBlankList(project.getTechnologies(), "project technologies");
            requiredNonBlankList(project.getVerification(), "project verification");
            require(hasText(project.getOutcome()), "project outcome is required: " + project.getId());
            require(hasText(project.getHandoff()), "project handoff is required: " + project.getId());
            require(project.getStatus() != null, "project status is required: " + project.getId());
            require(project.getContributionType() != null,
                    "project contributionType is required: " + project.getId());

            for (String claimId : requiredNonBlankList(project.getClaimIds(), "project claimIds")) {
                Claim claim = claimsById.get(claimId);
                require(claim != null, "project claim reference does not exist: " + claimId);
                require(project.getId().equals(claim.getSubjectId()),
                        "claim reference belongs to a different project: " + claimId);
            }

            for (String evidenceId : requiredNonBlankList(
                    project.getEvidenceIds(), "project evidenceIds")) {
                require(evidenceById.containsKey(evidenceId),
                        "project evidence reference does not exist: " + evidenceId);
            }
            for (String timelineEventId : requiredNonBlankList(
                    project.getTimelineEventIds(), "project timelineEventIds")) {
                require(timelineById.containsKey(timelineEventId),
                        "project timeline reference does not exist: " + timelineEventId);
            }
        }

        for (CaseStudy caseStudy : cases) {
            require(hasText(caseStudy.getCode()),
                    "case code is required: " + caseStudy.getId());
            require(hasText(caseStudy.getSlug()), "case slug is required");
            require(SLUG_PATTERN.matcher(caseStudy.getSlug()).matches(),
                    "case slug format is invalid: " + caseStudy.getSlug());
            require(caseStudy.getType() != null,
                    "case type is required: " + caseStudy.getId());
            require(hasText(caseStudy.getTitle()),
                    "case title is required: " + caseStudy.getId());
            require(hasText(caseStudy.getSummary()),
                    "case summary is required: " + caseStudy.getId());
            require(hasText(caseStudy.getProblem()),
                    "case problem is required: " + caseStudy.getId());
            requiredNonBlankList(caseStudy.getActions(), "case actions");
            validateNonBlankValues(requiredList(caseStudy.getDecisions(), "case decisions"),
                    "case decisions");
            requiredNonBlankList(caseStudy.getVerification(), "case verification");
            require(hasText(caseStudy.getOutcome()),
                    "case outcome is required: " + caseStudy.getId());
            requiredNonBlankList(caseStudy.getLimitations(), "case limitations");
            require(caseStudy.getAchievementStatus() != null,
                    "case achievementStatus is required: " + caseStudy.getId());
            require(caseStudy.getContributionType() != null,
                    "case contributionType is required: " + caseStudy.getId());

            if (caseStudy.getProjectId() != null) {
                require(hasText(caseStudy.getProjectId()),
                        "case projectId must not be blank: " + caseStudy.getId());
                require(projectsById.containsKey(caseStudy.getProjectId()),
                        "case project reference does not exist: " + caseStudy.getProjectId());
            }

            for (String claimId : requiredNonBlankList(
                    caseStudy.getClaimIds(), "case claimIds")) {
                Claim claim = claimsById.get(claimId);
                require(claim != null, "case claim reference does not exist: " + claimId);
                require(claim.getSubjectType() == ClaimSubjectType.CASE
                                && caseStudy.getId().equals(claim.getSubjectId()),
                        "claim reference belongs to a different case: " + claimId);
            }
            for (String evidenceId : requiredNonBlankList(
                    caseStudy.getEvidenceIds(), "case evidenceIds")) {
                require(evidenceById.containsKey(evidenceId),
                        "case evidence reference does not exist: " + evidenceId);
            }
            for (String timelineEventId : requiredNonBlankList(
                    caseStudy.getTimelineEventIds(), "case timelineEventIds")) {
                require(timelineById.containsKey(timelineEventId),
                        "case timeline reference does not exist: " + timelineEventId);
            }
            for (String questionPresetId : requiredNonBlankList(
                    caseStudy.getQuestionPresetIds(), "case questionPresetIds")) {
                require(questionsById.containsKey(questionPresetId),
                        "case question reference does not exist: " + questionPresetId);
            }
        }

        uniqueById(evidence, EvidenceRecord::getCode, "evidence code");
        for (TimelineEvent event : timeline) {
            require(hasText(event.getDateLabel()),
                    "timeline dateLabel is required: " + event.getId());
            require(hasText(event.getTitle()), "timeline title is required: " + event.getId());
            require(hasText(event.getProblem()), "timeline problem is required: " + event.getId());
            require(hasText(event.getAction()), "timeline action is required: " + event.getId());
            require(hasText(event.getImpact()), "timeline impact is required: " + event.getId());
            requireAssociation(event.getProjectIds(), event.getCaseIds(), "timeline");
            for (String projectId : event.getProjectIds()) {
                require(projectsById.containsKey(projectId),
                        "timeline project reference does not exist: " + projectId);
            }
            for (String caseId : event.getCaseIds()) {
                require(casesById.containsKey(caseId),
                        "timeline case reference does not exist: " + caseId);
            }
            for (String claimId : requiredNonBlankList(
                    event.getClaimIds(), "timeline claimIds")) {
                require(claimsById.containsKey(claimId),
                        "timeline claim reference does not exist: " + claimId);
            }
            for (String evidenceId : requiredNonBlankList(
                    event.getEvidenceIds(), "timeline evidenceIds")) {
                EvidenceRecord referenced = evidenceById.get(evidenceId);
                require(referenced != null,
                        "timeline evidence reference does not exist: " + evidenceId);
                require(referenced.getPublicStatus() == EvidenceStatus.APPROVED,
                        "timeline evidence must be APPROVED: " + evidenceId);
            }
        }
    }

    private static void validateVerification(Claim claim, List<ClaimEvidenceLink> links) {
        boolean hasDirect = links.stream().anyMatch(link -> link.getSupportType() == SupportType.DIRECT);
        if (isAchievement(claim.getAchievementStatus())) {
            require(hasDirect, "achievement claim requires an APPROVED DIRECT link: " + claim.getId());
        }
        if (claim.getVerificationBasis() == VerificationBasis.EVIDENCE_SUPPORTED) {
            require(hasDirect && claim.getVerificationStatus() == ClaimVerificationStatus.VERIFIED,
                    "claim verificationStatus does not match DIRECT evidence: " + claim.getId());
        } else if (claim.getVerificationBasis() == VerificationBasis.SELF_DECLARED
                || claim.getVerificationBasis() == VerificationBasis.INFERRED) {
            require(claim.getVerificationStatus() != ClaimVerificationStatus.VERIFIED,
                    "claim verificationStatus exceeds verificationBasis: " + claim.getId());
        } else if (claim.getVerificationBasis() == VerificationBasis.UNSUPPORTED) {
            require(claim.getVerificationStatus() == ClaimVerificationStatus.UNVERIFIED,
                    "claim verificationStatus exceeds unsupported basis: " + claim.getId());
        }
    }

    private static boolean isAchievement(AchievementStatus status) {
        return status == AchievementStatus.DELIVERED
                || status == AchievementStatus.IMPLEMENTED_TESTED
                || status == AchievementStatus.PROTOTYPE
                || status == AchievementStatus.DESIGNED;
    }

    private static <T> List<T> requiredList(List<T> value, String field) {
        require(value != null, field + " is required");
        return value;
    }

    private static List<String> requiredNonBlankList(List<String> value, String field) {
        require(value != null && !value.isEmpty(), field + " must not be empty");
        validateNonBlankValues(value, field);
        return value;
    }

    private static void requireAssociation(
            List<String> projectIds,
            List<String> caseIds,
            String type
    ) {
        List<String> requiredProjectIds = requiredList(projectIds, type + " projectIds");
        List<String> requiredCaseIds = requiredList(caseIds, type + " caseIds");
        require(!requiredProjectIds.isEmpty() || !requiredCaseIds.isEmpty(),
                type + " must reference at least one project or case");
        validateNonBlankValues(requiredProjectIds, type + " projectIds");
        validateNonBlankValues(requiredCaseIds, type + " caseIds");
    }

    private static void validateNonBlankValues(List<String> values, String field) {
        for (String item : values) {
            require(hasText(item), field + " must not contain blank values");
        }
    }

    private static void requireDisjoint(
            Set<String> first,
            Set<String> second,
            String message
    ) {
        require(first.stream().noneMatch(second::contains), message);
    }

    private static <T> Map<String, T> uniqueById(
            List<T> values,
            Function<T, String> idExtractor,
            String type
    ) {
        Map<String, T> byId = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (T value : values) {
            require(value != null, type + " item must not be null");
            String id = idExtractor.apply(value);
            require(hasText(id), type + " id is required");
            require(seen.add(id), "duplicate " + type + " id: " + id);
            byId.put(id, value);
        }
        return Map.copyOf(byId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPortfolioSnapshotException(message);
        }
    }
}
