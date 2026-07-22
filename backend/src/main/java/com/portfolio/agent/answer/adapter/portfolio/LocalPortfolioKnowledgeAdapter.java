package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.Map;
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
        return new RuntimeAnswerContent(
                snapshot.getContentVersion(),
                snapshot.getRuntimeBundleHash(),
                projects
        );
    }

    private AnswerKnowledge toKnowledge(RuntimeContentSnapshot snapshot, ProjectProfile value) {
        Set<String> evidenceIds = Set.copyOf(value.getEvidenceIds());

        List<AnswerQuestion> questions = snapshot.getQuestions().stream()
                .filter(candidate -> candidate.getProjectIds().contains(value.getId()))
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
        List<AnswerClaimProjection> claims = snapshot.getClaims().stream()
                .filter(claim -> value.getId().equals(claim.getSubjectId()))
                .map(claim -> new AnswerClaimProjection(
                        claim.getId(),
                        AnswerClaimCategory.valueOf(claim.getCategory().name()),
                        AnswerVerificationBasis.valueOf(claim.getVerificationBasis().name()),
                        AnswerClaimVerificationStatus.valueOf(claim.getVerificationStatus().name()),
                        AnswerMateriality.valueOf(claim.getMateriality().name()),
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
