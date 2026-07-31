package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskClassification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioTaskClassifierPort;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PortfolioTaskResolver {

    private static final Pattern REQUESTED_SIZE_PATTERN =
            Pattern.compile("([2-5二三四五两])\\s*(?:个|项|份|件)");
    private static final Map<String, String> AUDIENCE_ROLE_DICTIONARY = Map.of(
            "面试官", "INTERVIEWER",
            "招聘方", "INTERVIEWER",
            "招聘经理", "INTERVIEWER");
    private static final Map<String, String> CAREER_TRACK_DICTIONARY = Map.of(
            "后端", "BACKEND",
            "backend", "BACKEND",
            "前端", "FRONTEND",
            "frontend", "FRONTEND",
            "全栈", "FULL_STACK",
            "fullstack", "FULL_STACK");
    private static final Map<String, String> CAPABILITY_DICTIONARY = Map.ofEntries(
            Map.entry("java", "JAVA"),
            Map.entry("rag", "RAG"),
            Map.entry("spring", "SPRING"),
            Map.entry("postgresql", "POSTGRESQL"),
            Map.entry("pgvector", "PGVECTOR"),
            Map.entry("sql", "SQL"),
            Map.entry("vue", "VUE"),
            Map.entry("typescript", "TYPESCRIPT"),
            Map.entry("python", "PYTHON"));

    private final PortfolioTaskClassifierPort classifier;
    private final double confidenceThreshold;

    public PortfolioTaskResolver(
            PortfolioTaskClassifierPort classifier,
            double confidenceThreshold) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        if (!Double.isFinite(confidenceThreshold)
                || confidenceThreshold < 0.0d
                || confidenceThreshold > 1.0d) {
            throw new IllegalArgumentException(
                    "confidenceThreshold must be finite and between 0 and 1");
        }
        this.confidenceThreshold = confidenceThreshold;
    }

    public PortfolioTask resolve(
            String turnId,
            String question,
            PortfolioRecommendationContext recommendationContext) {
        PortfolioTaskMode ruleMode = resolveRule(question);
        if (ruleMode != null) {
            if (ruleMode == PortfolioTaskMode.REFINE_RECOMMENDATION
                    && recommendationContext == null) {
                return clarification(turnId, question, recommendationContext);
            }
            PortfolioConditions conditions = ruleMode == PortfolioTaskMode.RECOMMENDATION
                    ? extractControlledConditions(question)
                    : PortfolioConditions.empty();
            return task(turnId, question, ruleMode, 1.0d, conditions,
                    recommendationContext, null);
        }
        return resolveWithClassifier(turnId, question, recommendationContext);
    }

    public boolean matchesDeterministicRule(String question) {
        return resolveRule(question) != null;
    }

    private PortfolioTask resolveWithClassifier(
            String turnId,
            String question,
            PortfolioRecommendationContext recommendationContext) {
        ConversationModelResult<PortfolioTaskClassification> result =
                classifier.classifyPortfolioTask(turnId, question, recommendationContext);
        if (!result.isSuccessful()) {
            return clarification(turnId, question, recommendationContext);
        }
        PortfolioTaskClassification classification = result.getValue();
        if (classification.getConfidence() < confidenceThreshold) {
            return clarification(turnId, question, recommendationContext);
        }
        if (classification.getMode() == PortfolioTaskMode.REFINE_RECOMMENDATION
                && recommendationContext == null) {
            return clarification(turnId, question, recommendationContext);
        }
        return task(
                turnId,
                question,
                classification.getMode(),
                classification.getConfidence(),
                classification.getConditions(),
                recommendationContext,
                classification.getRefinement());
    }

    private PortfolioTaskMode resolveRule(String question) {
        String normalized = Objects.requireNonNull(question, "question").toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "换掉", "替换", "调整推荐", "再偏", "数量改")) {
            return PortfolioTaskMode.REFINE_RECOMMENDATION;
        }
        if (containsAny(normalized, "比较", "对比", "区别", "哪个好", "哪一个更")) {
            return PortfolioTaskMode.COMPARISON;
        }
        if (containsAny(normalized, "推荐", "适合我的作品", "适合我展示")) {
            return PortfolioTaskMode.RECOMMENDATION;
        }
        if (containsAny(normalized, "做了什么", "做过什么", "介绍这个项目", "介绍这个案例", "技术栈", "怎么实现")) {
            return PortfolioTaskMode.FACT_LOOKUP;
        }
        return null;
    }

    private PortfolioConditions extractControlledConditions(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return new PortfolioConditions(
                extractSingleControlledValue(normalized, CAREER_TRACK_DICTIONARY),
                extractSingleControlledValue(normalized, AUDIENCE_ROLE_DICTIONARY),
                extractCapabilityCodes(normalized),
                null,
                extractRequestedSize(normalized));
    }

    private String extractSingleControlledValue(
            String question,
            Map<String, String> dictionary) {
        Set<String> matches = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            if (containsControlledKeyword(question, entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    private Set<String> extractCapabilityCodes(String question) {
        Set<String> matches = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : CAPABILITY_DICTIONARY.entrySet()) {
            if (containsControlledKeyword(question, entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return Set.copyOf(matches);
    }

    private boolean containsControlledKeyword(String question, String keyword) {
        if (!keyword.chars().allMatch(character -> character < 128)) {
            return question.contains(keyword);
        }
        Pattern keywordPattern = Pattern.compile(
                "(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])");
        return keywordPattern.matcher(question).find();
    }

    private Integer extractRequestedSize(String question) {
        Matcher matcher = REQUESTED_SIZE_PATTERN.matcher(question);
        if (!matcher.find()) {
            return null;
        }
        return switch (matcher.group(1)) {
            case "2", "二", "两" -> 2;
            case "3", "三" -> 3;
            case "4", "四" -> 4;
            case "5", "五" -> 5;
            default -> null;
        };
    }

    private boolean containsAny(String question, String... phrases) {
        for (String phrase : phrases) {
            if (question.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private PortfolioTask clarification(
            String turnId,
            String question,
            PortfolioRecommendationContext recommendationContext) {
        return task(
                turnId,
                question,
                PortfolioTaskMode.CLARIFICATION_REQUIRED,
                1.0d,
                PortfolioConditions.empty(),
                recommendationContext,
                null);
    }

    private PortfolioTask task(
            String turnId,
            String question,
            PortfolioTaskMode mode,
            double confidence,
            PortfolioConditions conditions,
            PortfolioRecommendationContext recommendationContext,
            PortfolioRefinement refinement) {
        return new PortfolioTask(
                turnId,
                question,
                mode,
                confidence,
                conditions,
                recommendationContext,
                refinement);
    }
}
