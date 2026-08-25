package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.RagDocument;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

/**
 * RAG 文档内容哈希的计算工具。
 *
 * <p>把文档的全部内容字段（chunkId、contentVersion、slug 列表、claimIds、文本、
 * topics、有效期）按固定顺序以长度前缀编码写入字节流，再计算 SHA-256，
 * 前缀长度编码可消除字段边界歧义，保证哈希对相同内容稳定、对不同内容区分。
 * 不可实例化。
 */
public final class RagDocumentHashCalculator {

    private RagDocumentHashCalculator() {
    }

    /**
     * 计算文档的规范化内容哈希。
     *
     * @param document 待哈希的 RAG 文档
     * @return 形如 {@code sha256:<hex>} 的哈希字符串
     * @throws IllegalStateException SHA-256 不可用或编码失败（理论上不应发生）
     */
    public static String contentHash(RagDocument document) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(output);
            write(data, document.getChunkId());
            write(data, document.getContentVersion());
            write(data, document.getProjectSlugs());
            write(data, document.getCaseSlugs());
            write(data, document.getClaimIds());
            write(data, document.getText());
            write(data, document.getTopics());
            write(data, document.getValidFrom());
            write(data, document.getValidUntil());
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(output.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("unable to hash RAG document", exception);
        }
    }

    private static void write(DataOutputStream data, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static void write(DataOutputStream data, List<String> values) throws IOException {
        data.writeInt(values.size());
        for (String value : values) {
            write(data, value);
        }
    }

    private static void write(DataOutputStream data, LocalDate value) throws IOException {
        data.writeBoolean(value != null);
        if (value != null) {
            write(data, value.toString());
        }
    }
}
