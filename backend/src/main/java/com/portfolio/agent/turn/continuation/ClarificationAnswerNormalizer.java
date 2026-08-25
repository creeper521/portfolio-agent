package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.ClarificationProposal;
import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 将一次性澄清答案收敛为闭合值；访客原文不会进入返回对象。 */
public final class ClarificationAnswerNormalizer {

    /**
     * 把澄清答案归一化为闭合的 ResolutionValue。
     *
     * <p>优先采用 Store 内部绑定键携带的闭合值；文本答案仅在能唯一映射到
     * 公开主体、闭合枚举或固定别名时接受，否则返回 Optional.empty()，
     * 由上层以固定文案兜底。</p>
     */
    public Optional<BlockedGoalTemplate.ResolutionValue> normalize(
            BlockedGoalTemplate template,
            ClarificationStore.ResolvedAnswer answer,
            List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects) {
        if (template == null || answer == null) return Optional.empty();
        ClarificationProposal.Field field = template.getUnresolvedField();
        String binding = answer.bindingKey();
        String text = answer.text();
        return switch (field) {
            case REQUESTED_SIZE -> requestedSize(binding, text)
                    .map(BlockedGoalTemplate.RequestedSizeValue::new);
            case SUBJECT -> subject(binding, text, publicSubjects)
                    .map(value -> new BlockedGoalTemplate.SubjectValue(List.of(value)));
            case OUTPUT -> output(binding, text)
                    .map(value -> new BlockedGoalTemplate.OutputValue(Set.of(value)));
            case CONSTRAINT -> constraint(binding, text)
                    .map(value -> new BlockedGoalTemplate.ConstraintValue(Set.of(value)));
            case GOAL -> Optional.empty();
        };
    }

    /** 解析推荐数量：支持中文数字与阿拉伯数字 1..5，其余拒绝。 */
    private Optional<Integer> requestedSize(String binding, String text) {
        String candidate = valueAfter(binding, "size:").orElse(text);
        if (candidate == null) return Optional.empty();
        String normalized = candidate.trim();
        int value = switch (normalized) {
            case "一", "1" -> 1;
            case "二", "两", "2" -> 2;
            case "三", "3" -> 3;
            case "四", "4" -> 4;
            case "五", "5" -> 5;
            default -> -1;
        };
        return value < 1 ? Optional.empty() : Optional.of(value);
    }

    /** 解析公开主体：绑定键优先，否则按已审核别名在公开目录中唯一匹配。 */
    private Optional<BlockedGoalTemplate.Subject> subject(
            String binding,
            String text,
            List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects) {
        Optional<String> bound = valueAfter(binding, "subject:");
        if (bound.isPresent()) {
            String[] parts = bound.orElseThrow().split(":", 2);
            if (parts.length != 2) return Optional.empty();
            return publicSubjects.stream().filter(value ->
                            value.getKind().name().equals(parts[0])
                                    && value.getReference().equals(parts[1]))
                    .findFirst().map(value -> new BlockedGoalTemplate.Subject(
                            value.getKind(), value.getReference()));
        }
        if (text == null) return Optional.empty();
        String normalized = text.trim();
        List<GoalInterpretationInput.PublicSubjectDescriptor> matches = publicSubjects.stream()
                .filter(value -> value.matchesAlias(normalized)).toList();
        return matches.size() == 1
                ? Optional.of(new BlockedGoalTemplate.Subject(
                matches.getFirst().getKind(), matches.getFirst().getReference()))
                : Optional.empty();
    }

    /** 解析请求输出枚举名（大小写不敏感）。 */
    private Optional<GoalRequestedOutput> output(String binding, String text) {
        String value = valueAfter(binding, "output:").orElse(text);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(GoalRequestedOutput.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /** 解析约束：接受闭合命名或固定别名（PostgreSQL/后端/前端）。 */
    private Optional<String> constraint(String binding, String text) {
        Optional<String> bound = valueAfter(binding, "constraint:");
        if (bound.isPresent() && !bound.orElseThrow().equals("text")) {
            return bound.filter(this::closedName);
        }
        if (text == null) return Optional.empty();
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "postgresql", "postgres", "偏 postgresql", "偏postgresql" ->
                    Optional.of("POSTGRESQL");
            case "后端", "backend", "偏后端", "偏 backend", "偏backend" ->
                    Optional.of("BACKEND");
            case "前端", "frontend", "偏前端", "偏 frontend", "偏frontend" ->
                    Optional.of("FRONTEND");
            default -> Optional.empty();
        };
    }

    /** 取绑定键前缀之后的值；前缀不匹配返回 Optional.empty()。 */
    private Optional<String> valueAfter(String binding, String prefix) {
        return binding != null && binding.startsWith(prefix)
                ? Optional.of(binding.substring(prefix.length())) : Optional.empty();
    }

    /** 校验闭合命名格式（大写字母与下划线）。 */
    private boolean closedName(String value) {
        return value.matches("[A-Z_]{1,64}");
    }

}
