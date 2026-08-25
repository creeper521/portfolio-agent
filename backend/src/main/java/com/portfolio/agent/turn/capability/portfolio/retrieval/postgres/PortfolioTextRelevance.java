package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 公开段落文本相关性判定工具：以归一化词元匹配实现查询与段落内容的最小相关性检查。
 *
 * <p>分词策略区分中英文：中文按整词（不超过 4 码位）与相邻二元组（bigram）同时入表，
 * 以兼顾短词精确命中与长词部分命中；英文小写化并去除停用词，仅保留长度不小于 2 的词。
 * 该类只做布尔判定，不产生评分，用于降级检索路径的段落过滤。
 */
public final class PortfolioTextRelevance {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "how", "in", "is", "it", "of", "on", "or", "show", "that", "the",
            "this", "to", "was", "what", "when", "where", "which", "with");

    private PortfolioTextRelevance() {
    }

    /**
     * 判断段落内容是否与查询相关：查询任一词元（或中文二元组）出现在归一化内容中即相关。
     *
     * @param query   访问者查询文本；分词后为空（全停用词/空白）时恒为不相关
     * @param content 段落内容
     * @return 相关返回 true
     */
    public static boolean matches(String query, String content) {
        String normalizedContent = normalize(content);
        List<String> tokens = tokens(query);
        if (tokens.isEmpty()) {
            return false;
        }
        return tokens.stream().anyMatch(normalizedContent::contains);
    }

    /** 查询文本归一化分词：去停用词后产出中英文词元集合，见类注释中的分词策略。 */
    private static List<String> tokens(String query) {
        String normalized = normalize(query);
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.isBlank() || STOP_WORDS.contains(token)) {
                continue;
            }
            if (containsCjk(token)) {
                // 短中文词整词入表保证精确命中，同时补齐相邻二元组覆盖长词的部分匹配
                if (token.codePointCount(0, token.length()) <= 4) {
                    tokens.add(token);
                }
                List<Integer> codePoints = token.codePoints().boxed().toList();
                for (int index = 0; index + 1 < codePoints.size(); index++) {
                    tokens.add(new String(new int[]{
                            codePoints.get(index), codePoints.get(index + 1)}, 0, 2));
                }
            } else if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    /** 判断文本是否包含汉字（HAN 脚本）码位。 */
    private static boolean containsCjk(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    /** NFKC 归一化并小写化，消除全半角与大小写差异；null 视为空串。 */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip();
    }
}
