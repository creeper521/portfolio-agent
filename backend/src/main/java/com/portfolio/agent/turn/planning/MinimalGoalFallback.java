package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;

public final class MinimalGoalFallback {
    private static final Pattern ARABIC_COUNT = Pattern.compile(
            "(?<!\\d)(\\d+)\\s*(?:个|项(?!目))");
    private static final Pattern ARABIC_RANGE = Pattern.compile(
            "(?<!\\d)(\\d+)\\s*[-到至~～—]\\s*(\\d+)\\s*(?:个|项(?!目))");
    private static final Pattern CHINESE_COUNT = Pattern.compile(
            "([零〇一二两三四五六七八九十百]+)\\s*(?:个|项(?!目))");
    private static final Pattern CHINESE_RANGE = Pattern.compile(
            "([零〇一二两三四五六七八九十百]+)\\s*[-到至~～—]\\s*([零〇一二两三四五六七八九十百]+)\\s*(?:个|项(?!目))");
    private static final Pattern DIRECT_PROJECT_COUNT = Pattern.compile(
            "^\\s*(\\d+|[零〇一二两三四五六七八九十百]+)\\s*项目");

    /** 在模型前处理不会产生语义争议的安全社交输入和项目推荐数量。 */
    public Optional<ResolvedGoalSet> tryResolveBeforeProvider(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context) {
        if (!(command.getInput() instanceof AgentTurnCommand.FreeText freeText)) {
            return Optional.empty();
        }
        String text = freeText.getText().trim();
        String social = text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s，。！？!?,.～~]+", "");
        if (Set.of("你好", "您好", "嗨", "hello", "hi", "谢谢", "多谢", "感谢")
                .contains(social)) {
            return Optional.of(ResolvedGoalSet.conversational(
                    social.contains("谢") || social.equals("感谢")
                            ? "不客气，我可以继续介绍或比较公开项目。"
                            : "你好，我可以介绍、比较或推荐公开项目。"));
        }
        if (!text.contains("推荐") || !text.contains("项目")) {
            return Optional.empty();
        }
        if (text.matches(".*(?:不要|不用|无需|别)\\s*推荐.*项目.*")) {
            return Optional.empty();
        }
        Optional<Set<String>> recognizedConstraints = constraints(text);
        if (recognizedConstraints.isEmpty()) return Optional.empty();
        Set<Integer> counts = recommendationCounts(text);
        boolean invalid = counts.stream().anyMatch(value -> value < 1 || value > 5);
        if (invalid || counts.size() > 1) {
            BlockedGoalTemplate blocked = BlockedGoalTemplate.recommendation(
                    null, recognizedConstraints.orElseThrow(),
                    ClarificationProposal.Field.REQUESTED_SIZE);
            return Optional.of(ResolvedGoalSet.clarification(new ClarificationProposal(
                    ClarificationProposal.Field.REQUESTED_SIZE,
                    "请选择要推荐的项目数量（1—5 个）。", blocked)));
        }
        int requestedSize = counts.isEmpty() ? 2 : counts.iterator().next();
        return Optional.of(ResolvedGoalSet.goals(recommendationProposal(
                freeText.getText(), requestedSize, recognizedConstraints.orElseThrow())));
    }

    public Optional<UserGoalProposal> tryResolve(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context) {
        if (!(command.getInput() instanceof AgentTurnCommand.FreeText freeText)) {
            return Optional.empty();
        }
        String normalized = freeText.getText().trim();
        AgentTurnCommand.SubjectHint hint = command.getSurfaceContext().getSubjectHint();
        List<GoalInterpretationInput.PublicSubjectDescriptor> namedSubjects =
                context.getPublicSubjects().stream()
                        .filter(subject -> subject.getReviewedAliases().stream()
                                .anyMatch(alias -> normalized.contains(alias)))
                        .distinct().toList();
        if (normalized.contains("比较") && namedSubjects.size() == 2) {
            UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(
                    freeText.getText(), 0);
            List<GoalSubjectReference> references = namedSubjects.stream()
                    .map(subject -> new GoalSubjectReference(
                            subject.getKind(), subject.getReference(),
                            GoalSubjectReference.Basis.EXPLICIT_INPUT, anchor))
                    .toList();
            UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                    "portfolio-comparison", GoalKind.PORTFOLIO_COMPARE, anchor, references,
                    Set.of(GoalRequestedOutput.COMPARISON),
                    GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                    new UserGoalProposal.PortfolioCompareParameters(Set.of("IMPLEMENTATION")));
            return Optional.of(new UserGoalProposal(List.of(goal)));
        }
        for (GoalInterpretationInput.PublicSubjectDescriptor subject : context.getPublicSubjects()) {
            boolean exactText = subject.matchesAlias(normalized);
            boolean mentionsAlias = subject.getReviewedAliases().stream()
                    .anyMatch(alias -> normalized.contains(alias));
            boolean resolvedHint = hint != null
                    && hint.getKind().name().equals(subject.getKind().name())
                    && subject.matchesAlias(hint.getSlug())
                    && (mentionsAlias || referencesSurface(normalized));
            if (exactText || resolvedHint) {
                UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(
                        exactText ? normalized : freeText.getText(), 0);
                GoalSubjectReference reference = new GoalSubjectReference(
                        subject.getKind(), subject.getReference(),
                        exactText ? GoalSubjectReference.Basis.EXPLICIT_INPUT
                                : GoalSubjectReference.Basis.SURFACE_HINT,
                        exactText ? anchor : null);
                UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                        "portfolio-overview", GoalKind.PORTFOLIO_FACT, anchor,
                        List.of(reference), Set.of(GoalRequestedOutput.OVERVIEW),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.OVERVIEW)));
                return Optional.of(new UserGoalProposal(List.of(goal)));
            }
        }
        return Optional.empty();
    }

    private boolean referencesSurface(String text) {
        return text.contains("这个项目") || text.contains("该项目")
                || text.contains("这个案例") || text.contains("该案例")
                || text.contains("这个作品") || text.contains("该作品");
    }

    private Set<Integer> recommendationCounts(String text) {
        int recommendation = text.indexOf("推荐");
        int project = text.lastIndexOf("项目");
        if (recommendation < 0 || project <= recommendation) return Set.of();
        String quantityScope = text.substring(recommendation + 2, project + 2);
        Set<Integer> values = new LinkedHashSet<>();
        Matcher range = ARABIC_RANGE.matcher(quantityScope);
        while (range.find()) {
            values.add(safeCount(range.group(1)));
            values.add(safeCount(range.group(2)));
        }
        Matcher matcher = ARABIC_COUNT.matcher(quantityScope);
        while (matcher.find()) values.add(safeCount(matcher.group(1)));
        Matcher chineseRange = CHINESE_RANGE.matcher(quantityScope);
        while (chineseRange.find()) {
            values.add(chineseCount(chineseRange.group(1)));
            values.add(chineseCount(chineseRange.group(2)));
        }
        Matcher chinese = CHINESE_COUNT.matcher(quantityScope);
        while (chinese.find()) values.add(chineseCount(chinese.group(1)));
        if (values.isEmpty()) {
            Matcher direct = DIRECT_PROJECT_COUNT.matcher(quantityScope);
            if (direct.find()) {
                String raw = direct.group(1);
                values.add(raw.chars().allMatch(Character::isDigit)
                        ? safeCount(raw) : chineseCount(raw));
            }
        }
        return Set.copyOf(values);
    }

    private int safeCount(String digits) {
        try {
            BigInteger value = new BigInteger(digits);
            return value.compareTo(BigInteger.valueOf(5)) > 0
                    ? 6 : value.intValue();
        } catch (NumberFormatException invalid) {
            return 6;
        }
    }

    private int chineseCount(String text) {
        if (text.equals("十")) return 10;
        int hundred = text.indexOf('百');
        if (hundred >= 0) return 6;
        int ten = text.indexOf('十');
        if (ten >= 0) {
            int tens = ten == 0 ? 1 : chineseDigit(text.charAt(0));
            int ones = ten == text.length() - 1 ? 0 : chineseDigit(text.charAt(ten + 1));
            int value = tens * 10 + ones;
            return value > 5 ? 6 : value;
        }
        int value = text.length() == 1 ? chineseDigit(text.charAt(0)) : 6;
        return value > 5 ? 6 : value;
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '零', '〇' -> 0;
            default -> 6;
        };
    }

    private Optional<Set<String>> constraints(String text) {
        Set<String> values = new LinkedHashSet<>();
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        if (hasNegatedKnownConstraint(normalized)) return Optional.empty();
        if (positiveConstraint(normalized, "后端", "backend")) values.add("BACKEND");
        if (positiveConstraint(normalized, "前端", "frontend")) values.add("FRONTEND");
        if (positiveConstraint(normalized, "postgresql", "postgresql", "postgres")) {
            values.add("POSTGRESQL");
        }
        return Optional.of(Set.copyOf(values));
    }

    private boolean hasNegatedKnownConstraint(String text) {
        return negatedAlias(text, "后端", "backend")
                || negatedAlias(text, "前端", "frontend")
                || negatedAlias(text, "postgresql", "postgres");
    }

    private boolean negatedAlias(String text, String... aliases) {
        for (String alias : aliases) {
            int from = 0;
            while (true) {
                int index = text.indexOf(alias, from);
                if (index < 0) break;
                String prefix = text.substring(Math.max(0, index - 8), index);
                if (prefix.matches(".*(?:不要(?:选择|使用)?|不需要|不含|排除|别用|不想要?|无需)\\s*$")) {
                    return true;
                }
                from = index + alias.length();
            }
        }
        return false;
    }

    private boolean positiveConstraint(String text, String chinese, String... englishAliases) {
        boolean chinesePhrase = text.contains("偏" + chinese)
                || text.contains(chinese + "项目")
                || text.contains(chinese + "方向")
                || text.contains(chinese + "相关")
                || text.contains(chinese + "技术")
                || text.contains("面向" + chinese);
        boolean englishWord = java.util.Arrays.stream(englishAliases).anyMatch(english ->
                Pattern.compile("(?<![a-z0-9])" + Pattern.quote(english) + "(?![a-z0-9])")
                        .matcher(text).find());
        return chinesePhrase || englishWord;
    }

    private UserGoalProposal recommendationProposal(
            String input, int requestedSize, Set<String> constraints) {
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(input, 0);
        UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                "portfolio-recommendation", GoalKind.PORTFOLIO_RECOMMEND,
                anchor, List.of(), Set.of(GoalRequestedOutput.RECOMMENDATION),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.PortfolioRecommendationParameters(
                        requestedSize, constraints));
        return new UserGoalProposal(List.of(goal));
    }
}
