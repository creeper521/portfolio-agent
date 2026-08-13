package com.portfolio.agent.answer.composition.validation;

import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProtectedAtomExtractor {
    private static final Pattern HIGH_RISK_LITERAL = Pattern.compile(
            "(?iu)(?:v?\\d+(?:[.,]\\d+)*(?:%|％|ms|s|秒|分钟|小时|天|元|万元|亿|kb|mb|gb|tb|次|个|项|人|年|月|日)?|"
            + "\\d{4}[-/.年]\\d{1,2}(?:[-/.月]\\d{1,2}日?)?|"
            + "[A-Z][A-Za-z0-9.+#-]{1,})");
    private static final List<String> CONTROLLED_WORDS = List.of(
            "最高", "最低", "更高", "更低", "优于", "劣于", "排名", "第一", "唯一", "最优",
            "没有", "并未",
            "因为", "由于", "因此", "所以", "导致", "证明", "验证", "独立完成", "全部完成", "完整覆盖",
            "已交付", "已上线", "生产环境", "主要负责", "主导",
            "production", "complete", "proven", "verified",
            "delivered", "independent", "primary", "best", "only");
    private static final Pattern HAN_SEQUENCE = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final List<String> SAFE_CHINESE_EXPRESSION_TERMS = List.of(
            "计划", "拟", "将要", "原型", "试验", "实验", "观察", "部分", "局部", "阶段性",
            "尚未", "未覆盖", "不确定", "参与", "协作", "支持", "可能", "推测", "倾向",
            "通过", "使用", "完成", "结果", "系统", "项目", "目前", "仍", "已经", "团队");

    public boolean isSubsetOfSupportedAtoms(String draft, List<GroundedStatement> supported) {
        Set<String> source = sourceAtoms(supported);
        return source.containsAll(extract(draft)) && containsOnlySupportedChineseContent(draft, supported);
    }

    public boolean preservesSupportedAtoms(String draft, List<GroundedStatement> supported) {
        Set<String> required = new LinkedHashSet<>();
        for (GroundedStatement statement : supported) {
            required.addAll(extract(statement.getPublicStatement()));
        }
        Set<String> actual = extract(draft);
        return actual.containsAll(required);
    }

    Set<String> extract(String value) {
        LinkedHashSet<String> atoms = new LinkedHashSet<>();
        if (value == null) return atoms;
        String normalized = normalize(value);
        Matcher matcher = HIGH_RISK_LITERAL.matcher(normalized);
        while (matcher.find()) atoms.add(matcher.group().toLowerCase(Locale.ROOT));
        for (String word : CONTROLLED_WORDS) {
            if (normalized.toLowerCase(Locale.ROOT).contains(word)) atoms.add(word);
        }
        return atoms;
    }

    private Set<String> sourceAtoms(List<GroundedStatement> supported) {
        LinkedHashSet<String> atoms = new LinkedHashSet<>();
        for (GroundedStatement statement : supported) {
            atoms.addAll(extract(statement.getPublicStatement()));
            atoms.addAll(extract(statement.getPublicDetail()));
        }
        // A subject is protected when explicitly named by the draft; omission may be a safe pronoun.
        return atoms;
    }

    public boolean containsOnlyKnownSubjects(String draft, List<GroundedStatement> supported,
            Set<String> allPublicSubjectLabels) {
        String normalized = normalize(draft).toLowerCase(Locale.ROOT);
        Set<String> allowed = new LinkedHashSet<>();
        supported.forEach(statement -> statement.getSubjectReferences().forEach(subject ->
                allowed.add(normalize(subject.getPublicLabel()).toLowerCase(Locale.ROOT))));
        for (String label : allPublicSubjectLabels) {
            String candidate = normalize(label).toLowerCase(Locale.ROOT);
            if (normalized.contains(candidate) && !allowed.contains(candidate)) return false;
        }
        return true;
    }

    private boolean containsOnlySupportedChineseContent(String draft,
            List<GroundedStatement> supported) {
        StringBuilder source = new StringBuilder();
        for (GroundedStatement statement : supported) {
            source.append(normalize(statement.getPublicStatement())).append(' ');
            source.append(normalize(statement.getPublicDetail())).append(' ');
        }
        String candidate = normalize(draft);
        for (String safeTerm : SAFE_CHINESE_EXPRESSION_TERMS) {
            candidate = candidate.replace(safeTerm, " ");
        }
        // Single Han characters are predominantly particles. Any new multi-character sequence is
        // treated as a possible entity, technology, action or relation and must occur in support.
        Matcher matcher = HAN_SEQUENCE.matcher(candidate);
        while (matcher.find()) {
            if (source.indexOf(matcher.group()) < 0) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC);
    }
}
