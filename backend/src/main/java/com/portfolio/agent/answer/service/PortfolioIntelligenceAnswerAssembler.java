package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.service.ContractEvidenceSelector;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PortfolioIntelligenceAnswerAssembler {

    public ConversationAnswerResult assemble(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            PortfolioDecision decision
    ) {
        Objects.requireNonNull(decision, "decision");
        if (decision.getDisposition() == PortfolioDisposition.NOT_PORTFOLIO) {
            throw new IllegalArgumentException(
                    "not-portfolio decision cannot be assembled as an answer");
        }
        PortfolioIntelligenceResult result = decision.getMaterial().orElseThrow();
        AnswerResolution resolution = switch (decision.getDisposition()) {
            case ANSWERED -> AnswerResolution.ANSWERED;
            case NEEDS_CLARIFICATION -> AnswerResolution.NEEDS_CLARIFICATION;
            case NOT_SUPPORTED -> AnswerResolution.NOT_SUPPORTED;
            case CAPABILITY_UNAVAILABLE -> AnswerResolution.CAPABILITY_UNAVAILABLE;
            case NOT_PORTFOLIO -> throw new IllegalStateException(
                    "not-portfolio decision cannot be assembled as an answer");
        };
        List<ConversationAnswerBlock> blocks = decision.getDisposition()
                == PortfolioDisposition.NEEDS_CLARIFICATION
                ? clarificationBlocks(result)
                : materialBlocks(result);
        return new ConversationAnswerResult(
                request.getTurnId(),
                contentVersion(content, result),
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                resolution,
                title(result.getResolvedIntent()),
                blocks,
                List.of(),
                result.isDegraded(),
                GenerationMode.DETERMINISTIC,
                AnswerSource.RETRIEVAL,
                result.getNoticeCode(),
                new com.portfolio.agent.answer.domain.ConversationProgress(
                        List.of(),
                        com.portfolio.agent.answer.domain.ConversationGuidanceStage.OPENING),
                result.getPortfolioRecommendation(),
                AnswerConstructionMode.EVIDENCE_COMPOSITION,
                Objects.requireNonNull(result.getIntentSource(), "intentSource"),
                decision.getDisposition() == PortfolioDisposition.ANSWERED
                        ? AnswerEvidenceState.VERIFIED
                        : AnswerEvidenceState.INSUFFICIENT)
                .withContextVersionUpdated(result.isContextVersionUpdated())
                .withContractIdentity(result.getQuestionPresetId(), result.getContractVersion());
    }

    public ConversationAnswerResult assemble(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            PortfolioIntelligenceResult intelligenceResult) {
        return assemble(
                request,
                content,
                intelligenceResult,
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO);
    }

    public ConversationAnswerResult assemble(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            PortfolioIntelligenceResult intelligenceResult,
            ConversationIntent intent,
            ConversationAnswerScope answerScope) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(intelligenceResult, "intelligenceResult");
        boolean clarification = intelligenceResult.getResolvedIntent()
                == PortfolioTaskMode.CLARIFICATION_REQUIRED;
        List<ConversationAnswerBlock> blocks = clarification
                ? clarificationBlocks(intelligenceResult)
                : materialBlocks(intelligenceResult);
        return new ConversationAnswerResult(
                request.getTurnId(),
                contentVersion(content, intelligenceResult),
                intent,
                answerScope,
                clarification ? AnswerResolution.BOUNDARY : AnswerResolution.ANSWERED,
                title(intelligenceResult.getResolvedIntent()),
                blocks,
                List.of(),
                intelligenceResult.isDegraded(),
                GenerationMode.DETERMINISTIC,
                AnswerSource.RETRIEVAL,
                intelligenceResult.getNoticeCode(),
                new com.portfolio.agent.answer.domain.ConversationProgress(
                        List.of(),
                        com.portfolio.agent.answer.domain.ConversationGuidanceStage.OPENING),
                intelligenceResult.getPortfolioRecommendation());
    }

    public PortfolioGroundingContext grounding(PortfolioIntelligenceResult result) {
        Objects.requireNonNull(result, "result");
        ConversationSubjectOption subject = result.getSubjects().isEmpty()
                ? null
                : subject(result.getSubjects().getFirst());
        Map<String, AnswerClaimProjection> claimsById = new LinkedHashMap<>();
        result.getEvidence().forEach(passage -> claimsById.putIfAbsent(
                passage.getClaimId(),
                new AnswerClaimProjection(
                        passage.getClaimId(),
                        AnswerClaimCategory.IMPLEMENTATION,
                        passage.getContent(),
                        passage.getContent(),
                        AnswerAchievementStatus.UNKNOWN,
                        AnswerContributionType.PRIMARY,
                        AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                        AnswerClaimVerificationStatus.VERIFIED,
                        AnswerMateriality.KEY,
                        passage.getEvidenceIds())));
        Map<String, AnswerEvidence> evidenceById = new LinkedHashMap<>();
        result.getEvidence().forEach(passage -> passage.getEvidenceReferences().forEach(reference ->
                evidenceById.putIfAbsent(reference.getEvidenceId(), new AnswerEvidence(
                        reference.getEvidenceId(),
                        reference.getLabel(),
                        "PUBLIC_REFERENCE",
                        null,
                        null,
                        1,
                        reference.getLabel(),
                        reference.getPublicStatus(),
                        false))));
        List<AnswerRetrievalChunk> chunks = result.getEvidence().stream()
                .map(passage -> new AnswerRetrievalChunk(
                        passage.getPassageId(),
                        List.of(),
                        List.of(),
                        List.of(passage.getClaimId()),
                        List.of(),
                        passage.getContent(),
                        passage.getContent().length()))
                .toList();
        return new PortfolioGroundingContext(
                subject,
                List.copyOf(claimsById.values()),
                List.copyOf(evidenceById.values()),
                chunks);
    }

    private ConversationSubjectOption subject(
            com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject subject) {
        return new ConversationSubjectOption(
                AnswerSubjectType.valueOf(subject.getSubjectType()),
                subject.getPortfolioId(),
                subject.getTitle(),
                subject.getSummary());
    }

    private List<ConversationAnswerBlock> clarificationBlocks(
            PortfolioIntelligenceResult intelligenceResult) {
        return List.of(block(
                intelligenceResult.getClarification().getQuestion(),
                List.of(),
                List.of()));
    }

    private List<ConversationAnswerBlock> materialBlocks(
            PortfolioIntelligenceResult intelligenceResult) {
        if (!intelligenceResult.getEvidence().isEmpty()) {
            return intelligenceResult.getEvidence().stream()
                    .map(this::passageBlock)
                    .toList();
        }
        if ("PRESET_CONTRACT_STALE".equals(intelligenceResult.getNoticeCode())) {
            return List.of(block("这个推荐问题正在更新，请刷新后重试。", List.of(), List.of()));
        }
        if (ContractEvidenceSelector.UNAVAILABLE_NOTICE.equals(intelligenceResult.getNoticeCode())) {
            return List.of(block("这个推荐问题暂时无法回答，内容正在更新。", List.of(), List.of()));
        }
        PortfolioRecommendation recommendation = intelligenceResult.getPortfolioRecommendation();
        if (recommendation != null) {
            String content = recommendation.getItems().isEmpty()
                    ? "当前没有作品满足全部推荐条件，可以放宽一项条件后继续调整。"
                    : "已根据公开且经过验证的证据生成 "
                            + recommendation.getItems().size() + " 项确定性推荐。";
            List<String> evidenceIds = recommendation.getItems().stream()
                    .flatMap(item -> item.getEvidenceIds().stream())
                    .distinct()
                    .toList();
            return List.of(block(content, List.of(), evidenceIds));
        }
        return List.of(block(
                "当前公开内容中没有足够的已验证材料。",
                List.of(),
                List.of()));
    }

    private ConversationAnswerBlock passageBlock(PortfolioRetrievedPassage passage) {
        return block(passage.getContent(), List.of(passage.getClaimId()), passage.getEvidenceIds());
    }

    private ConversationAnswerBlock block(
            String content,
            List<String> claimIds,
            List<String> evidenceIds) {
        return new ConversationAnswerBlock(
                ConversationSourceScope.PORTFOLIO,
                content,
                claimIds,
                evidenceIds);
    }

    private String contentVersion(
            RuntimeAnswerContent content,
            PortfolioIntelligenceResult result) {
        if (result.getContentVersion() != null) {
            return result.getContentVersion();
        }
        PortfolioRecommendation recommendation = result.getPortfolioRecommendation();
        return recommendation == null
                ? content.getContentVersion()
                : recommendation.getContext().getContentVersion();
    }

    private String title(PortfolioTaskMode mode) {
        return switch (mode) {
            case FACT_LOOKUP -> "作品集信息";
            case COMPARISON -> "作品集比较";
            case RECOMMENDATION -> "作品集推荐";
            case REFINE_RECOMMENDATION -> "已调整推荐";
            case CLARIFICATION_REQUIRED -> "需要补充信息";
        };
    }
}
