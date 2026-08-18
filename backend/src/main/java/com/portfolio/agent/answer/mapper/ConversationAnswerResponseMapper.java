package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSourceComposition;
import com.portfolio.agent.answer.domain.AnswerSupportKind;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.dto.response.AnswerBlockSupportResponse;
import com.portfolio.agent.answer.dto.response.ConversationAnswerBlockResponse;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.dto.response.ConversationResponse;
import com.portfolio.agent.answer.dto.response.ConversationSuggestedQuestionResponse;
import com.portfolio.agent.answer.dto.response.PortfolioRecommendationResponse;
import com.portfolio.agent.answer.dto.response.PublicSourceCatalogEntryResponse;
import com.portfolio.agent.answer.dto.response.PublicSourceReferenceResponse;
import com.portfolio.agent.answer.dto.response.StatementSupportReferenceResponse;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Temporary Slice-1/2 response mapper; no internal execution model is projected. */
@Component
public final class ConversationAnswerResponseMapper {
    public ConversationAnswerResponse toResponse(ConversationAnswerResult result) {
        return toResponse(result, null, Map.of());
    }

    public ConversationAnswerResponse toResponse(
            ConversationAnswerResult result, ConversationResponse conversation) {
        return toResponse(result, conversation, Map.of());
    }

    public ConversationAnswerResponse toResponse(
            ConversationAnswerResult result,
            ConversationResponse conversation,
            Map<String, ContextHandle> contextHandles) {
        AnswerResolution resolution = result.getResolution() == AnswerResolution.BOUNDARY
                ? AnswerResolution.NEEDS_CLARIFICATION : result.getResolution();
        boolean answerLike = resolution == AnswerResolution.ANSWERED
                || resolution == AnswerResolution.PARTIALLY_ANSWERED;
        List<ConversationAnswerBlockResponse> blocks = answerLike
                ? mapBlocks(result.getBlocks()) : redactBlocks(result.getBlocks());
        AnswerSourceComposition composition = answerLike ? sourceComposition(blocks) : null;
        List<PublicSourceCatalogEntryResponse> catalog = answerLike
                ? publicSourceCatalog(blocks) : List.of();
        return new ConversationAnswerResponse(
                result.getTurnId(), result.getContentVersion(), result.getIntent(),
                publicScope(result.getAnswerScope()), resolution, result.getTitle(), blocks,
                result.getSuggestedQuestions().stream()
                        .map(ConversationSuggestedQuestionResponse::from).toList(),
                result.isDegraded(), result.getConstructionMode(), result.getIntentSource(),
                result.getEvidenceState(), result.getNoticeCode(),
                result.getProgress().getCoveredTopics(), result.getProgress().getStage(),
                result.getPortfolioRecommendation() == null ? null
                        : PortfolioRecommendationResponse.from(result.getPortfolioRecommendation()),
                result.isContextVersionUpdated(), result.getQuestionPresetId(),
                result.getContractVersion(), result.getSummary(), null,
                "ANSWER", conversation, composition, catalog, null, null);
    }

    private List<ConversationAnswerBlockResponse> mapBlocks(List<ConversationAnswerBlock> values) {
        List<ConversationAnswerBlockResponse> blocks = new ArrayList<>();
        for (ConversationAnswerBlock block : values) {
            boolean portfolio = block.getSourceScope() == ConversationSourceScope.PORTFOLIO;
            if (portfolio && block.getSourceReferences().isEmpty()) continue;
            List<PublicSourceReferenceResponse> references = block.getSourceReferences().stream()
                    .map(PublicSourceReferenceResponse::from).toList();
            List<String> publicKeys = references.stream()
                    .map(PublicSourceReferenceResponse::getReferenceKey).toList();
            List<StatementSupportReferenceResponse> statements = block.getClaimIds().stream()
                    .map(id -> new StatementSupportReferenceResponse(id, publicKeys, null)).toList();
            AnswerBlockSupportResponse support = new AnswerBlockSupportResponse(
                    portfolio ? AnswerSupportKind.VERIFIED_PUBLIC_EVIDENCE
                            : AnswerSupportKind.GENERAL_KNOWLEDGE,
                    statements, publicKeys, null);
            blocks.add(new ConversationAnswerBlockResponse(
                    stableId(block), portfolio
                            ? SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                            : SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                    block.getSourceScope(), block.getSectionType(), block.getTitle(),
                    block.getContent(), List.of(), List.of(), references, support));
        }
        return List.copyOf(blocks);
    }

    private List<ConversationAnswerBlockResponse> redactBlocks(List<ConversationAnswerBlock> values) {
        return values.stream().map(block -> new ConversationAnswerBlockResponse(
                block.getSourceScope(), block.getSectionType(), block.getTitle(),
                block.getContent(), List.of(), List.of(), List.of())).toList();
    }

    private AnswerSourceComposition sourceComposition(List<ConversationAnswerBlockResponse> blocks) {
        boolean portfolio = blocks.stream().anyMatch(block ->
                block.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO);
        boolean general = blocks.stream().anyMatch(block ->
                block.getSourceDomain() == SemanticRoutingTypes.TaskSourceDomain.GENERAL);
        if (portfolio && general) return AnswerSourceComposition.MULTI_SOURCE;
        if (portfolio) return AnswerSourceComposition.PORTFOLIO_ONLY;
        if (general) return AnswerSourceComposition.GENERAL_ONLY;
        return null;
    }

    private List<PublicSourceCatalogEntryResponse> publicSourceCatalog(
            List<ConversationAnswerBlockResponse> blocks) {
        Map<String, PublicSourceCatalogEntryResponse> entries = new LinkedHashMap<>();
        for (ConversationAnswerBlockResponse block : blocks) {
            for (PublicSourceReferenceResponse reference : block.getSourceReferences()) {
                entries.putIfAbsent(reference.getReferenceKey(), new PublicSourceCatalogEntryResponse(
                        reference.getReferenceKey(), reference.getLabel(), reference.getPublishedVersion(),
                        reference.getSourceType(), reference.getSubjectRoute(), reference.getEvidenceRoute()));
            }
        }
        return List.copyOf(entries.values());
    }

    private ConversationAnswerScope publicScope(ConversationAnswerScope scope) {
        return switch (scope) {
            case CONVERSATION -> ConversationAnswerScope.GLOBAL;
            case HYBRID -> ConversationAnswerScope.MIXED;
            case GENERAL, PORTFOLIO, GLOBAL, MIXED -> scope;
        };
    }

    private String stableId(ConversationAnswerBlock block) {
        try {
            String canonical = block.getSourceScope() + "\n" + block.getSectionType()
                    + "\n" + block.getTitle() + "\n" + block.getContent();
            return "block-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
