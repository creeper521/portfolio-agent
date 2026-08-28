package com.portfolio.agent.turn.capability.general;

import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 通用能力草稿的语义校验器：在 {@link GeneralDraftCodec} 结构解码通过后，
 * 再按请求意图校验草稿的可确定语义（主题一致、角色结构、可信 role aspects、中文句式），
 * 通过则升格为 {@link GeneralSemanticResult}。
 *
 * <p>fail-closed：任何语义偏差都以封闭的
 * {@link GeneralDraftValidationException.Reason} 拒绝，异常文本不携带
 * Provider 原始输出。校验规则按请求 Kind 分叉——EXPLANATION 要求
 * "定义 + 机制"两条陈述、深度决定句数区间且 aspect 只标记相同 role；COMPARISON 要求
 * 全部陈述为对比角色、subject×dimension 配对精确覆盖且不重复、不携带 aspects。
 */
public final class GeneralDraftValidator {
    /**
     * 校验草稿并升格为语义结果：先核对主题（EXPLANATION 用 topic，
     * COMPARISON 用 "A vs B" 拼接），再按 Kind 走对应的质量/覆盖校验，
     * 最后按 canonical 版本校验 caveats 句式及历史兼容约束。
     *
     * @throws GeneralDraftValidationException 任一语义规则不满足；Reason 标识具体类别
     */
    public GeneralSemanticResult validate(
            GeneralKnowledgeRequest request, GeneralDraftCodec.Draft draft) {
        if (!GeneralDraftRules.topic(request).equals(draft.topic())) {
            reject(GeneralDraftValidationException.Reason.TOPIC_MISMATCH,
                    "draft topic does not match request");
        }
        if (request.getKind() == GeneralKnowledgeRequest.Kind.EXPLANATION) {
            if (draft.statements().size() != 2
                    || draft.statements().get(0).role()
                            != GeneralSemanticResult.Role.DEFINITION
                    || draft.statements().get(1).role()
                            != GeneralSemanticResult.Role.MECHANISM
                    || draft.statements().stream().anyMatch(value ->
                            value.subject() != null || value.dimension() != null)) {
                reject(GeneralDraftValidationException.Reason.EXPLANATION_ROLES_INVALID,
                        "explanation roles are invalid");
            }
            validateExplanationQuality(request, draft);
        } else {
            if (draft.statements().stream().anyMatch(value ->
                    value.role() != GeneralSemanticResult.Role.COMPARISON
                            || value.subject() == null
                            || value.dimension() == null)) {
                reject(GeneralDraftValidationException.Reason.COMPARISON_ROLE_INVALID,
                        "comparison contains an invalid role or pair");
            }
        }
        List<GeneralSemanticResult.Statement> statements = draft.statements().stream()
                .map(value -> new GeneralSemanticResult.Statement(
                        value.role(), value.text(), value.subject(), value.dimension()))
                .toList();
        if (request.getKind() == GeneralKnowledgeRequest.Kind.COMPARISON) {
            validateComparisonCoverage(request, statements);
            if (draft.statements().stream().anyMatch(value -> !value.aspects().isEmpty())) {
                reject(GeneralDraftValidationException.Reason.COMPARISON_ASPECTS_INVALID,
                        "comparison aspects must be empty");
            }
            statements.forEach(statement -> validateHistoricalChinesePeriodSentences(
                    statement.getText(), 1, 3, "comparison text"));
        }
        validateCaveats(request, draft);
        return new GeneralSemanticResult(
                draft.topic(), statements,
                draft.caveats().stream().map(GeneralDraftCodec.CaveatDraft::text).toList(),
                request.getExpectedContentVersion());
    }

    /**
     * EXPLANATION 质量校验：按深度（CONCISE/STANDARD/DETAILED）约束每条陈述的
     * 中文总句数区间；要求首条陈述标注 DEFINITION、次条标注 MECHANISM，且全部
     * 两条 statement 只携带与自身 role 相同的可信 aspect，不用关键词伪证细粒度覆盖。
     */
    private void validateExplanationQuality(
            GeneralKnowledgeRequest request, GeneralDraftCodec.Draft draft) {
        GeneralDraftRules.ExplanationRule rule = GeneralDraftRules.explanation(
                request.getDepth());
        int definitionSentences = countChineseSentences(
                draft.statements().get(0).text(), "definition text");
        int mechanismSentences = countChineseSentences(
                draft.statements().get(1).text(), "mechanism text");
        if (definitionSentences < rule.minimumSentencesPerRole()
                || definitionSentences > rule.maximumSentencesPerRole()
                || mechanismSentences < rule.minimumSentencesPerRole()
                || mechanismSentences > rule.maximumSentencesPerRole()) {
            reject(GeneralDraftValidationException.Reason
                            .LANGUAGE_OR_SENTENCE_COUNT_INVALID,
                    "explanation text has invalid language or sentence count");
        }
        int sentenceCount = definitionSentences + mechanismSentences;
        if (sentenceCount < rule.minimumCanonicalSentences()
                || sentenceCount > rule.maximumCanonicalSentences()) {
            reject(GeneralDraftValidationException.Reason
                            .LANGUAGE_OR_SENTENCE_COUNT_INVALID,
                    "explanation text has invalid language or sentence count");
        }
        if (!draft.statements().get(0).aspects().equals(
                Set.copyOf(rule.definitionAspects()))
                || !draft.statements().get(1).aspects().equals(
                Set.copyOf(rule.mechanismAspects()))) {
            reject(GeneralDraftValidationException.Reason.EXPLANATION_ROLE_ASPECTS_INVALID,
                    "explanation role aspects are invalid");
        }
    }

    /**
     * COMPARISON 覆盖校验：所有陈述必须为对比角色，subject×dimension 配对
     * 以 "\u0000" 拼接为键去重并与请求期望集合精确比对——配对重复、缺失、
     * 多余或数量不符均拒绝，保证对比矩阵既无空洞也无私货。
     */
    private void validateComparisonCoverage(
            GeneralKnowledgeRequest request, List<GeneralSemanticResult.Statement> statements) {
        if (statements.stream().anyMatch(value -> value.getRole() != GeneralSemanticResult.Role.COMPARISON)) {
            reject(GeneralDraftValidationException.Reason.COMPARISON_ROLE_INVALID,
                    "comparison contains an invalid role");
        }
        Set<String> expected = new HashSet<>();
        for (String subject : request.getSubjects()) {
            for (String dimension : request.getDimensions()) {
                expected.add(subject + "\u0000" + dimension);
            }
        }
        Set<String> actual = new HashSet<>();
        for (GeneralSemanticResult.Statement statement : statements) {
            String pair = statement.getSubject() + "\u0000" + statement.getDimension();
            if (!actual.add(pair)) {
                reject(GeneralDraftValidationException.Reason.COMPARISON_DUPLICATE_PAIR,
                        "comparison contains a duplicate pair");
            }
        }
        if (!actual.equals(expected) || statements.size() != expected.size()) {
            reject(GeneralDraftValidationException.Reason.COMPARISON_COVERAGE_INVALID,
                    "comparison pairs do not match request");
        }
    }

    /** v3 caveat 可同类并列；v2 保留已发布的 kind/text 唯一性与句号规则。 */
    private void validateCaveats(
            GeneralKnowledgeRequest request, GeneralDraftCodec.Draft draft) {
        List<GeneralDraftCodec.CaveatDraft> caveats = draft.caveats();
        boolean legacyV2 = "general.draft.v2".equals(draft.schemaVersion());
        Set<String> texts = new HashSet<>();
        Set<GeneralDraftCodec.CaveatKind> kinds = EnumSet.noneOf(
                GeneralDraftCodec.CaveatKind.class);
        for (GeneralDraftCodec.CaveatDraft caveat : caveats) {
            if (legacyV2 || request.getKind()
                    == GeneralKnowledgeRequest.Kind.COMPARISON) {
                validateHistoricalChinesePeriodSentences(
                        caveat.text(), 1, 2, "caveat");
            } else {
                validateChineseSentences(caveat.text(), 1, 2, "caveat");
            }
            if (legacyV2 && (!texts.add(normalize(caveat.text()))
                    || !kinds.add(caveat.kind()))) {
                reject(GeneralDraftValidationException.Reason.CAVEAT_DUPLICATE,
                        "caveats contain duplicates");
            }
        }
    }

    /**
     * 中文句式校验：必须以"。"结尾且不含其他句末标点（. ! ? ！ ？），
     * 中英文分号作为句内分句符保留，
     * 按"。"切分后句数落在 [minimum, maximum]，且每句至少含一个汉字
     * （防止非中文内容混入）。任一条件不满足即以对应 Reason 拒绝。
     */
    private void validateChineseSentences(
            String text, int minimum, int maximum, String name) {
        int sentenceCount = countChineseSentences(text, name);
        if (sentenceCount < minimum || sentenceCount > maximum) {
            reject(GeneralDraftValidationException.Reason.LANGUAGE_OR_SENTENCE_COUNT_INVALID,
                    name + " has invalid language or sentence count");
        }
    }

    private int countChineseSentences(String text, String name) {
        int count = 0;
        int segmentStart = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            int next = index + Character.charCount(codePoint);
            if ("。！？!?".indexOf(codePoint) >= 0) {
                String segment = text.substring(segmentStart, index);
                if (!GeneralDraftRules.isChineseDominant(segment)) {
                    reject(GeneralDraftValidationException.Reason
                                    .LANGUAGE_OR_SENTENCE_COUNT_INVALID,
                            name + " has invalid language or sentence count");
                }
                count++;
                segmentStart = next;
            }
            index = next;
        }
        String suffix = text.substring(segmentStart);
        if (count == 0 || suffix.codePoints().anyMatch(codePoint ->
                "”’」』）》】".indexOf(codePoint) < 0
                        && !Character.isWhitespace(codePoint))) {
            reject(GeneralDraftValidationException.Reason.SENTENCE_BOUNDARY_INVALID,
                    name + " has invalid sentence boundaries");
        }
        return count;
    }

    /** v2 Comparison 的历史边界：只认中文句号，不接纳其他句末符或引号后缀。 */
    private void validateHistoricalChinesePeriodSentences(
            String text, int minimum, int maximum, String name) {
        if (!text.endsWith("。") || text.matches(".*[.!?！？].*")) {
            reject(GeneralDraftValidationException.Reason.SENTENCE_BOUNDARY_INVALID,
                    name + " has invalid sentence boundaries");
        }
        List<String> sentences = java.util.Arrays.stream(text.split("。", -1))
                .filter(value -> !value.isBlank())
                .toList();
        if (sentences.size() < minimum || sentences.size() > maximum
                || sentences.stream().anyMatch(value ->
                        !GeneralDraftRules.isChineseDominant(value))) {
            reject(GeneralDraftValidationException.Reason
                            .LANGUAGE_OR_SENTENCE_COUNT_INVALID,
                    name + " has invalid language or sentence count");
        }
    }

    /** 统一拒绝出口：所有校验失败都转换为带封闭 Reason 的校验异常。 */
    private void reject(GeneralDraftValidationException.Reason reason, String message) {
        throw new GeneralDraftValidationException(reason, message);
    }

    /** 文本归一化：去首尾空白、转小写、连续空白折叠为单空格，用于重复检测。 */
    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
