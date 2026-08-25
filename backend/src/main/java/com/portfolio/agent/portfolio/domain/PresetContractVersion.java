package com.portfolio.agent.portfolio.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 预设契约版本工具：为单个预设问题计算其契约版本号。
 *
 * <p>契约版本号只覆盖影响回答契约的字段（id/text/aliases/contractSubjectId/
 * requiredClaimIds/supportingClaimIds/evidenceRequirement/contractStatus），
 * 展示类字段（如 displayOrder、placements）变化不会改变版本号。
 * 私有构造器，仅提供静态方法。
 */
public final class PresetContractVersion {

    private PresetContractVersion() {
    }

    /**
     * 计算单个预设的契约版本号："pcv1-" 前缀 + SHA-256 摘要前 8 字节的十六进制。
     *
     * <p>算法：把上述契约字段按固定顺序拼成规范化键值文本（每个字符串先经
     * {@link #normalize(String)} 归一化），对全文做 SHA-256 并截取前 8 字节。
     * 前缀 pcv1 标识算法代次，便于未来更换派生方式时区分。
     */
    public static String calculate(
            String id,
            String text,
            List<String> aliases,
            String contractSubjectId,
            List<String> requiredClaimIds,
            List<String> supportingClaimIds,
            QuestionEvidenceRequirement evidenceRequirement,
            PresetContractStatus contractStatus
    ) {
        String canonical = "id=" + normalize(id) + "\n"
                + "text=" + normalize(text) + "\n"
                + "aliases=" + String.join(",", normalized(aliases)) + "\n"
                + "subject=" + normalize(contractSubjectId) + "\n"
                + "requiredClaimIds=" + String.join(",", normalized(requiredClaimIds)) + "\n"
                + "supportingClaimIds=" + String.join(",", normalized(supportingClaimIds)) + "\n"
                + "minimumApprovedEvidencePerRequiredClaim="
                + evidenceRequirement.getMinimumApprovedEvidencePerRequiredClaim() + "\n"
                + "publicOnly=" + evidenceRequirement.isPublicOnly() + "\n"
                + "status=" + contractStatus.name().toLowerCase(Locale.ROOT) + "\n";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "pcv1-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 对列表中每个字符串做 {@link #normalize(String)} 归一化，保持顺序不变。
     */
    private static List<String> normalized(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(normalize(value));
        }
        return result;
    }

    /**
     * 归一化单个字符串：NFKC 规范化、转小写、去首尾空白、内部连续空白压缩为单个空格；
     * null 视为空串。目的在于让全半角/大小写/空白差异不产生不同的契约版本号。
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip()
                .replaceAll("\\s+", " ");
    }
}
