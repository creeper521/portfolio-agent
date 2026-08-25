package com.portfolio.agent.portfolio.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 预设契约集哈希工具：为全部 ACTIVE 预设问题计算整体契约指纹。
 *
 * <p>发布包构建与加载两侧各自计算该哈希并比对，确保对外生效的预设契约集合
 * （哪些问题、各是哪个契约版本）完全一致。私有构造器，仅提供静态方法。
 */
public final class PresetContractSetHash {

    private PresetContractSetHash() {
    }

    /**
     * 计算 ACTIVE 预设集合的 SHA-256 契约哈希（"sha256:" 前缀 + 十六进制）。
     *
     * <p>规则：先逐一校验每个预设必须为 ACTIVE、id 与契约版本非空、id 不得重复；
     * 然后按 id 升序把每个预设表示为 {@code {"presetId":...,"contractVersion":...}}
     * 并拼接成规范 JSON 数组，对其做 SHA-256。排序保证相同集合无论输入顺序如何
     * 都得到相同哈希。
     *
     * @param activeQuestions 待参与计算的预设问题列表
     * @return "sha256:" 前缀的十六进制哈希
     * @throws IllegalArgumentException 存在非 ACTIVE 预设、id/契约版本为空或 id 重复
     */
    public static String calculate(List<QuestionDefinition> activeQuestions) {
        List<QuestionDefinition> active = List.copyOf(activeQuestions);
        for (QuestionDefinition question : active) {
            if (!question.isActiveContract()) {
                throw new IllegalArgumentException(
                        "non-active preset in contract set: " + question.getId());
            }
            String id = question.getId();
            String version = question.getContractVersion();
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("active preset id is empty");
            }
            if (version == null || version.isEmpty()) {
                throw new IllegalArgumentException(
                        "active preset contract version is empty: " + id);
            }
        }
        long distinctIds = active.stream()
                .map(QuestionDefinition::getId)
                .distinct()
                .count();
        if (distinctIds != active.size()) {
            throw new IllegalArgumentException("duplicate active preset id");
        }
        String canonical = active.stream()
                .sorted(Comparator.comparing(QuestionDefinition::getId))
                .map(question -> "{\"presetId\":\"" + question.getId()
                        + "\",\"contractVersion\":\"" + question.getContractVersion() + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
        return "sha256:" + sha256Hex(canonical);
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     *
     * @throws IllegalStateException 运行环境缺少 SHA-256 算法（正常 JDK 不会发生）
     */
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
