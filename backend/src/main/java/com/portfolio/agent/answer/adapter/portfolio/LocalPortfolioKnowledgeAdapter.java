package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.AnswerTimelineEvent;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class LocalPortfolioKnowledgeAdapter implements PortfolioKnowledgeGateway {

    private final PublicPortfolioRepository repository;

    public LocalPortfolioKnowledgeAdapter(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    @Override
    public RuntimeAnswerContent getContent() {
        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        List<AnswerKnowledge> projects = snapshot.getProjects().stream()
                .map(project -> toKnowledge(snapshot, project))
                .toList();
        List<AnswerKnowledge> cases = snapshot.getCases().stream()
                .map(caseStudy -> toCaseKnowledge(snapshot, caseStudy))
                .toList();
        return new RuntimeAnswerContent(
                snapshot.getContentVersion(),
                snapshot.getRuntimeBundleHash(),
                projects,
                cases,
                snapshot.getRetrievalContent().map(this::toRetrievalCorpus).orElse(null),
                toTimeline(snapshot)
        );
    }

    private AnswerKnowledge toCaseKnowledge(
            RuntimeContentSnapshot snapshot,
            CaseStudy value
    ) {
        Set<String> evidenceIds = Set.copyOf(value.getEvidenceIds());
        List<AnswerQuestion> questions = snapshot.getQuestions().stream()
                .filter(candidate -> candidate.getCaseIds().contains(value.getId()))
                .filter(candidate -> candidate.getProjectIds().isEmpty())
                .map(this::toQuestion)
                .toList();
        List<AnswerEvidence> evidence = snapshot.getEvidence().stream()
                .filter(candidate -> evidenceIds.contains(candidate.getId()))
                .filter(candidate -> candidate.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(candidate -> Boolean.FALSE.equals(candidate.getRawContentPublic()))
                .map(this::toEvidence)
                .toList();
        Set<String> approvedEvidenceIds = evidence.stream()
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, List<String>> directEvidenceByClaimId = snapshot.getClaimEvidenceLinks().stream()
                .filter(link -> link.getSupportType()
                        == com.portfolio.agent.portfolio.domain.SupportType.DIRECT)
                .filter(link -> link.getReviewStatus()
                        == com.portfolio.agent.portfolio.domain.ReviewStatus.APPROVED)
                .filter(link -> approvedEvidenceIds.contains(link.getEvidenceId()))
                .collect(Collectors.groupingBy(
                        com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getClaimId,
                        Collectors.mapping(
                                com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getEvidenceId,
                                Collectors.toUnmodifiableList())));
        Map<String, Claim> claimsById = snapshot.getClaims().stream()
                .filter(claim -> claim.getSubjectType() == ClaimSubjectType.CASE)
                .filter(claim -> value.getId().equals(claim.getSubjectId()))
                .collect(Collectors.toUnmodifiableMap(Claim::getId, claim -> claim));
        List<AnswerClaimProjection> claims = value.getClaimIds().stream()
                .map(claimsById::get)
                .filter(Objects::nonNull)
                .map(claim -> new AnswerClaimProjection(
                        claim.getId(),
                        AnswerClaimCategory.valueOf(claim.getCategory().name()),
                        claim.getStatement(),
                        claim.getDetail(),
                        AnswerAchievementStatus.valueOf(claim.getAchievementStatus().name()),
                        AnswerContributionType.valueOf(claim.getContributionType().name()),
                        AnswerVerificationBasis.valueOf(claim.getVerificationBasis().name()),
                        AnswerClaimVerificationStatus.valueOf(claim.getVerificationStatus().name()),
                        AnswerMateriality.valueOf(claim.getMateriality().name()),
                        claim.getTopics(),
                        directEvidenceByClaimId.getOrDefault(claim.getId(), List.of())))
                .toList();
        return new AnswerKnowledge(
                AnswerSubjectType.CASE,
                value.getSlug(),
                value.getTitle(),
                value.getSummary(),
                value.getProblem(),
                value.getActions(),
                value.getSummary(),
                value.getDecisions(),
                value.getVerification(),
                value.getOutcome(),
                String.join(" ", value.getLimitations()),
                value.getAchievementStatus().name(),
                questions,
                evidence,
                claims);
    }

    private List<AnswerTimelineEvent> toTimeline(RuntimeContentSnapshot snapshot) {
        Map<String, ProjectProfile> projectsById = snapshot.getProjects().stream()
                .collect(Collectors.toUnmodifiableMap(
                        ProjectProfile::getId,
                        project -> project));
        Map<String, CaseStudy> casesById = snapshot.getCases().stream()
                .collect(Collectors.toUnmodifiableMap(
                        CaseStudy::getId,
                        caseStudy -> caseStudy));
        Map<String, Claim> claimsById = snapshot.getClaims().stream()
                .collect(Collectors.toUnmodifiableMap(Claim::getId, claim -> claim));
        Set<String> approvedEvidenceIds = snapshot.getEvidence().stream()
                .filter(evidence -> evidence.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(evidence -> Boolean.FALSE.equals(evidence.getRawContentPublic()))
                .map(EvidenceRecord::getId)
                .collect(Collectors.toUnmodifiableSet());
        return snapshot.getTimeline().stream()
                .filter(event -> event.getProjectIds().isEmpty()
                        != event.getCaseIds().isEmpty())
                .filter(event -> projectsById.keySet().containsAll(event.getProjectIds()))
                .filter(event -> casesById.keySet().containsAll(event.getCaseIds()))
                .filter(event -> event.getClaimIds().stream().allMatch(claimId -> {
                    Claim claim = claimsById.get(claimId);
                    return claim != null && (claim.getSubjectType() == ClaimSubjectType.PROJECT
                            ? event.getProjectIds().contains(claim.getSubjectId())
                            : event.getCaseIds().contains(claim.getSubjectId()));
                }))
                .filter(event -> approvedEvidenceIds.containsAll(event.getEvidenceIds()))
                .filter(event -> event.getEvidenceIds().stream().allMatch(evidenceId ->
                        event.getProjectIds().stream()
                                .map(projectsById::get)
                                .anyMatch(project -> project.getEvidenceIds().contains(evidenceId))
                        || event.getCaseIds().stream()
                                .map(casesById::get)
                                .anyMatch(caseStudy ->
                                        caseStudy.getEvidenceIds().contains(evidenceId))))
                .map(event -> new AnswerTimelineEvent(
                        event.getId(),
                        event.getDateLabel(),
                        event.getTitle(),
                        event.getProblem(),
                        event.getAction(),
                        event.getImpact(),
                        event.getProjectIds().stream()
                                .map(projectsById::get)
                                .map(ProjectProfile::getSlug)
                                .toList(),
                        event.getCaseIds().stream()
                                .map(casesById::get)
                                .map(CaseStudy::getSlug)
                                .toList(),
                        event.getClaimIds(),
                        event.getEvidenceIds()))
                .toList();
    }

    private AnswerRetrievalCorpus toRetrievalCorpus(RuntimeRetrievalContent source) {
        List<AnswerKeywordIndex.DocumentEntry> keywordDocuments = source.getKeywordIndex()
                .getDocuments().stream()
                .map(item -> new AnswerKeywordIndex.DocumentEntry(
                        item.getChunkId(), item.getDocumentLength(), item.getTermFrequencies()))
                .toList();
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                source.getKeywordIndex().getDocumentCount(),
                source.getKeywordIndex().getAverageDocumentLength(),
                keywordDocuments,
                source.getKeywordIndex().getDocumentFrequencies());
        Map<String, AnswerRetrievalChunk> chunks = source.getDocuments().stream()
                .collect(Collectors.toUnmodifiableMap(
                        com.portfolio.agent.portfolio.domain.RagDocument::getChunkId,
                        item -> new AnswerRetrievalChunk(
                                item.getChunkId(), item.getProjectSlugs(), item.getCaseSlugs(),
                                item.getClaimIds(),
                                item.getTopics(), item.getText(), item.getText().length())));
        return new AnswerRetrievalCorpus(
                keywordIndex, source.getVectorIndex().getVectors(), chunks,
                source.getManifest().getEmbeddingModelId(),
                source.getManifest().getEmbeddingArtifactSha256(),
                source.getManifest().getDimension());
    }

    private AnswerKnowledge toKnowledge(RuntimeContentSnapshot snapshot, ProjectProfile value) {
        Set<String> evidenceIds = Set.copyOf(value.getEvidenceIds());

        List<AnswerQuestion> questions = snapshot.getQuestions().stream()
                .filter(candidate -> candidate.getProjectIds().contains(value.getId()))
                .filter(candidate -> candidate.getCaseIds().isEmpty())
                .map(this::toQuestion)
                .toList();

        List<AnswerEvidence> evidence = snapshot.getEvidence().stream()
                .filter(candidate -> evidenceIds.contains(candidate.getId()))
                .filter(candidate -> candidate.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(candidate -> Boolean.FALSE.equals(candidate.getRawContentPublic()))
                .map(this::toEvidence)
                .toList();
        Set<String> approvedEvidenceIds = evidence.stream()
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, List<String>> directEvidenceByClaimId = snapshot.getClaimEvidenceLinks().stream()
                .filter(link -> link.getSupportType()
                        == com.portfolio.agent.portfolio.domain.SupportType.DIRECT)
                .filter(link -> link.getReviewStatus()
                        == com.portfolio.agent.portfolio.domain.ReviewStatus.APPROVED)
                .filter(link -> approvedEvidenceIds.contains(link.getEvidenceId()))
                .collect(Collectors.groupingBy(
                        com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getClaimId,
                        Collectors.mapping(
                                com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getEvidenceId,
                                Collectors.toUnmodifiableList())));
        Map<String, Claim> projectClaimsById = snapshot.getClaims().stream()
                .filter(claim -> claim.getSubjectType() == ClaimSubjectType.PROJECT)
                .filter(claim -> value.getId().equals(claim.getSubjectId()))
                .collect(Collectors.toUnmodifiableMap(Claim::getId, claim -> claim));
        List<AnswerClaimProjection> claims = value.getClaimIds().stream()
                .map(projectClaimsById::get)
                .filter(Objects::nonNull)
                .map(claim -> new AnswerClaimProjection(
                        claim.getId(),
                        AnswerClaimCategory.valueOf(claim.getCategory().name()),
                        claim.getStatement(),
                        claim.getDetail(),
                        AnswerAchievementStatus.valueOf(claim.getAchievementStatus().name()),
                        AnswerContributionType.valueOf(claim.getContributionType().name()),
                        AnswerVerificationBasis.valueOf(claim.getVerificationBasis().name()),
                        AnswerClaimVerificationStatus.valueOf(claim.getVerificationStatus().name()),
                        AnswerMateriality.valueOf(claim.getMateriality().name()),
                        claim.getTopics(),
                        directEvidenceByClaimId.getOrDefault(claim.getId(), List.of())))
                .toList();

        return new AnswerKnowledge(
                value.getSlug(),
                value.getTitle(),
                value.getSummary(),
                value.getBackground(),
                value.getResponsibilities(),
                value.getSolution(),
                value.getKeyDecisions(),
                value.getVerification(),
                value.getOutcome(),
                value.getHandoff(),
                value.getStatus().name(),
                questions,
                evidence,
                claims
        );
    }

    private AnswerQuestion toQuestion(QuestionDefinition question) {
        return new AnswerQuestion(
                question.getId(),
                question.getText(),
                question.getAliases(),
                question.getText()
        );
    }

    private AnswerEvidence toEvidence(EvidenceRecord evidence) {
        return new AnswerEvidence(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getType().name(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getPublicStatus().name(),
                false
        );
    }
}
