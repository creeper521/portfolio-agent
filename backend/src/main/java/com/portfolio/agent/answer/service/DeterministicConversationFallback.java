package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DeterministicConversationFallback {

    public ConversationAnswerResult answer(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content
    ) {
        String normalized = normalize(request.getQuestion());
        if (isGreeting(normalized)) {
            return result(
                    request,
                    content,
                    ConversationIntent.CONVERSATION,
                    ConversationAnswerScope.CONVERSATION,
                    AnswerResolution.ANSWERED,
                    "你好",
                    "你好，我可以回答通用技术问题，也可以结合公开作品集介绍项目实现、决策、困难和验证过程。",
                    List.of(),
                    List.of(),
                    false);
        }
        if (containsAny(normalized, List.of("密码", "token", "密钥", "私有", "未公开"))) {
            return boundary(
                    request,
                    content,
                    ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                    AnswerResolution.REJECTED,
                    "这个请求涉及私密或越权信息，无法提供。",
                    false);
        }
        if (containsAny(normalized, List.of("最新", "今天", "实时", "当前版本"))) {
            return boundary(
                    request,
                    content,
                    ConversationIntent.TIME_SENSITIVE,
                    AnswerResolution.BOUNDARY,
                    "这个问题需要实时联网核验；当前版本没有联网能力。",
                    false);
        }
        ConversationAnswerResult preset = presetAnswer(request, content);
        if (preset != null) {
            return preset;
        }
        return boundary(
                request,
                content,
                ConversationIntent.GENERAL_KNOWLEDGE,
                AnswerResolution.BOUNDARY,
                "当前 AI 生成能力暂不可用。你仍可以从已发布的作品集问题继续了解项目。",
                true);
    }

    public ConversationAnswerResult answer(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationRoute route
    ) {
        if (route.getIntent() == ConversationIntent.TIME_SENSITIVE
                || route.getIntent() == ConversationIntent.UNSUPPORTED_OR_UNSAFE
                || route.getIntent() == ConversationIntent.CONVERSATION) {
            return answer(request, content);
        }
        ConversationAnswerResult preset = presetAnswer(request, content);
        if (preset != null) {
            return preset;
        }
        return boundary(
                request,
                content,
                route.getIntent(),
                AnswerResolution.BOUNDARY,
                "当前 AI 生成能力暂不可用，且没有匹配到可安全降级的已发布答案。",
                true);
    }

    private ConversationAnswerResult presetAnswer(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content
    ) {
        for (AnswerKnowledge knowledge : java.util.stream.Stream.concat(
                        content.getProjects().stream(),
                        content.getCases().stream()).toList()) {
            if (!matchesPreset(knowledge, request.getQuestion())) {
                continue;
            }
            AnswerClaimProjection claim = knowledge.getClaims().stream()
                    .filter(item -> !item.getDirectEvidenceIds().isEmpty())
                    .findFirst()
                    .orElse(null);
            if (claim == null) {
                continue;
            }
            Set<String> directIds = Set.copyOf(claim.getDirectEvidenceIds());
            List<String> evidenceIds = knowledge.getEvidence().stream()
                    .map(AnswerEvidence::getId)
                    .filter(directIds::contains)
                    .toList();
            if (evidenceIds.isEmpty()) {
                continue;
            }
            String text = knowledge.getSummary();
            if (knowledge.getSolution() != null && !knowledge.getSolution().isBlank()) {
                text = text + "\n\n" + knowledge.getSolution();
            }
            return result(
                    request,
                    content,
                    ConversationIntent.PORTFOLIO_GROUNDED,
                    ConversationAnswerScope.PORTFOLIO,
                    AnswerResolution.ANSWERED,
                    knowledge.getTitle(),
                    text,
                    List.of(claim.getId()),
                    evidenceIds,
                    true);
        }
        return null;
    }

    private boolean matchesPreset(AnswerKnowledge knowledge, String question) {
        String normalized = normalize(question);
        for (AnswerQuestion preset : knowledge.getQuestions()) {
            if (normalize(preset.getCanonicalQuestion()).equals(normalized)
                    || preset.getAliases().stream()
                            .map(this::normalize)
                            .anyMatch(normalized::equals)) {
                return true;
            }
        }
        return false;
    }

    private ConversationAnswerResult boundary(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationIntent intent,
            AnswerResolution resolution,
            String message,
            boolean degraded
    ) {
        return result(
                request,
                content,
                intent,
                ConversationAnswerScope.CONVERSATION,
                resolution,
                resolution == AnswerResolution.REJECTED ? "无法处理" : "能力边界",
                message,
                List.of(),
                List.of(),
                degraded);
    }

    private ConversationAnswerResult result(
            ConversationAnswerRequest request,
            RuntimeAnswerContent content,
            ConversationIntent intent,
            ConversationAnswerScope scope,
            AnswerResolution resolution,
            String title,
            String text,
            List<String> claimIds,
            List<String> evidenceIds,
            boolean degraded
    ) {
        ConversationSourceScope sourceScope = scope == ConversationAnswerScope.PORTFOLIO
                ? ConversationSourceScope.PORTFOLIO
                : ConversationSourceScope.GENERAL;
        return new ConversationAnswerResult(
                request.getTurnId(),
                content.getContentVersion(),
                intent,
                scope,
                resolution,
                title,
                List.of(new ConversationAnswerBlock(
                        sourceScope, text, claimIds, evidenceIds)),
                List.of(),
                degraded);
    }

    private boolean isGreeting(String value) {
        return value.matches("(你好|您好|嗨|哈喽|hello|hi|谢谢|感谢|再见|拜拜)");
    }

    private boolean containsAny(String value, List<String> markers) {
        return markers.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s！!。,.，?？]", "");
    }
}
