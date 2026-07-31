package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.ConversationTopic;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ConversationProgressClassifier {

    private static final List<String> EXPLORE_MARKERS = List.of(
            "推荐其他项目",
            "还有什么项目",
            "换个项目",
            "别的项目",
            "其他作品");

    public ConversationProgress classify(
            List<ConversationTopic> priorTopics,
            String question,
            PortfolioKnowledgeFacet facet
    ) {
        LinkedHashSet<ConversationTopic> covered =
                new LinkedHashSet<>(priorTopics);
        if (isExploreOthers(question)) {
            return new ConversationProgress(
                    List.copyOf(covered),
                    ConversationGuidanceStage.EXPLORE_OTHERS);
        }
        PortfolioKnowledgeFacet resolvedFacet =
                facet == null ? inferFacet(question) : facet;
        covered.add(toTopic(resolvedFacet));
        ConversationGuidanceStage stage;
        if (covered.size() <= 2) {
            stage = ConversationGuidanceStage.OPENING;
        }
        else if (covered.size() <= 4) {
            stage = ConversationGuidanceStage.DEEPENING;
        }
        else {
            stage = ConversationGuidanceStage.WRAP_UP;
        }
        return new ConversationProgress(List.copyOf(covered), stage);
    }

    public PortfolioKnowledgeFacet inferFacet(String question) {
        String normalized = question == null
                ? ""
                : question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "职责", "负责", "贡献", "边界")) {
            return PortfolioKnowledgeFacet.RESPONSIBILITY;
        }
        if (containsAny(normalized, "取舍", "为什么", "替代", "权衡")) {
            return PortfolioKnowledgeFacet.DECISION;
        }
        if (containsAny(normalized, "失败", "故障", "困难", "排查", "事故")) {
            return PortfolioKnowledgeFacet.INCIDENT;
        }
        if (containsAny(normalized, "验证", "测试", "证据", "证明")) {
            return PortfolioKnowledgeFacet.VERIFICATION;
        }
        if (containsAny(normalized, "结果", "效果", "状态", "局限", "产出")) {
            return PortfolioKnowledgeFacet.OUTCOME;
        }
        if (containsAny(normalized, "实现", "方案", "架构", "怎么做")) {
            return PortfolioKnowledgeFacet.IMPLEMENTATION;
        }
        return PortfolioKnowledgeFacet.OVERVIEW;
    }

    private boolean isExploreOthers(String question) {
        String normalized = question == null ? "" : question;
        return EXPLORE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private ConversationTopic toTopic(PortfolioKnowledgeFacet facet) {
        return switch (facet) {
            case OVERVIEW -> ConversationTopic.BACKGROUND;
            case RESPONSIBILITY -> ConversationTopic.RESPONSIBILITY;
            case IMPLEMENTATION -> ConversationTopic.SOLUTION;
            case DECISION -> ConversationTopic.TRADEOFF;
            case CHALLENGE, INCIDENT -> ConversationTopic.FAILURE;
            case VERIFICATION -> ConversationTopic.VERIFICATION;
            case LIMITATION, LEARNING, OUTCOME -> ConversationTopic.OUTCOME;
        };
    }
}
