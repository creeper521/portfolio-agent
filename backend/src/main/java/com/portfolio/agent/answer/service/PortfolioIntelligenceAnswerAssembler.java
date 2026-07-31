package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import java.util.List;
import java.util.Objects;

public final class PortfolioIntelligenceAnswerAssembler {

    public ConversationAnswerResult assemble(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            PortfolioIntelligenceResult intelligenceResult) {
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
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
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
