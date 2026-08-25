package com.portfolio.agent.infrastructure.retrieval.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 本地 embedding 工件校验器：加载打包的 BGE 模型描述符，并核对磁盘上的
 * 本地模型文件与描述符逐项一致（路径、大小、SHA-256）。
 *
 * <p>这是本地公开检索（BGE 路径）的隐私与配置门之一：只有目录真实存在、
 * 描述符格式封闭合法、每个文件哈希与大小完全匹配时才放行初始化；
 * 任何偏差都以封闭 code 的 {@link LocalEmbeddingFailureException} 拒绝，
 * 不回显文件内容或绝对路径。描述符中的相对路径被强制限制在模型目录内，
 * 杜绝路径穿越。
 */
public final class LocalEmbeddingArtifactVerifier {
    private static final String DESCRIPTOR_RESOURCE =
            "/embedding-models/bge-small-zh-v1.5-int8.json";

    /**
     * 仅加载并校验打包描述符，不接触磁盘模型文件。
     *
     * @return 描述符解析出的工件元数据
     * @throws LocalEmbeddingFailureException 描述符缺失（LOCAL_MODEL_DESCRIPTOR_MISSING）
     *         或内容非法（LOCAL_MODEL_DESCRIPTOR_INVALID）
     */
    public LocalEmbeddingArtifact descriptor() {
        try (InputStream input = getClass().getResourceAsStream(DESCRIPTOR_RESOURCE)) {
            if (input == null) {
                throw failure("LOCAL_MODEL_DESCRIPTOR_MISSING");
            }
            byte[] bytes = input.readAllBytes();
            JsonNode descriptor = new ObjectMapper().readTree(bytes);
            requireDescriptor(descriptor);
            return toArtifact(descriptor, sha256(bytes));
        } catch (LocalEmbeddingFailureException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
        }
    }

    /**
     * 校验模型目录并核对全部工件：目录必须真实存在，描述符列出的每个文件
     * 必须是目录内的常规文件，且大小与 SHA-256 与描述符完全一致。
     *
     * @param modelDirectory 配置的本地模型目录
     * @return 描述符解析出的工件元数据
     * @throws LocalEmbeddingFailureException 目录非法（LOCAL_MODEL_DIRECTORY_INVALID）、
     *         描述符缺失或非法，或任一文件不匹配（LOCAL_MODEL_ARTIFACT_MISMATCH）
     */
    public LocalEmbeddingArtifact verify(Path modelDirectory) {
        if (modelDirectory == null || !Files.isDirectory(modelDirectory)) {
            throw failure("LOCAL_MODEL_DIRECTORY_INVALID");
        }
        try (InputStream input = getClass().getResourceAsStream(DESCRIPTOR_RESOURCE)) {
            if (input == null) {
                throw failure("LOCAL_MODEL_DESCRIPTOR_MISSING");
            }
            byte[] bytes = input.readAllBytes();
            JsonNode descriptor = new ObjectMapper().readTree(bytes);
            requireDescriptor(descriptor);
            Path root = modelDirectory.toAbsolutePath().normalize();
            for (JsonNode file : descriptor.path("files")) {
                String relativeName = requiredText(file, "path");
                Path candidate = root.resolve(relativeName).normalize();
                // startsWith 双重防线：描述符校验已拒绝绝对路径与 ".."，
                // 这里再确保解析后的候选文件仍位于模型目录内。
                if (!candidate.startsWith(root)
                        || !Files.isRegularFile(candidate)
                        || Files.size(candidate) != file.path("size").asLong()
                        || !sha256(candidate).equals(
                                "sha256:" + requiredText(file, "sha256"))) {
                    throw failure("LOCAL_MODEL_ARTIFACT_MISMATCH");
                }
            }
            return toArtifact(descriptor, sha256(bytes));
        } catch (LocalEmbeddingFailureException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw failure("LOCAL_MODEL_ARTIFACT_MISMATCH");
        }
    }

    /**
     * 校验描述符的封闭结构：schemaVersion 必须为 1.0，数值字段必须为正，
     * files 必须是非空数组，且每个文件项的路径必须是相对路径（拒绝绝对路径
     * 与 ".."）、大小为正、SHA-256 为 64 位十六进制。
     */
    private void requireDescriptor(JsonNode descriptor) {
        if (!"1.0".equals(requiredText(descriptor, "schemaVersion"))
                || descriptor.path("dimension").asInt() <= 0
                || descriptor.path("maxTokens").asInt() <= 0
                || descriptor.path("intraOpNumThreads").asInt() <= 0
                || descriptor.path("interOpNumThreads").asInt() <= 0
                || !descriptor.path("files").isArray()
                || descriptor.path("files").isEmpty()) {
            throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
        }
        requiredText(descriptor, "modelId");
        requiredText(descriptor, "queryInstruction");
        for (JsonNode file : descriptor.path("files")) {
            String path = requiredText(file, "path");
            String hash = requiredText(file, "sha256");
            if (Path.of(path).isAbsolute() || path.contains("..")
                    || file.path("size").asLong() <= 0
                    || !hash.matches("[0-9a-f]{64}")) {
                throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
            }
        }
    }

    /** 把已校验的描述符 JSON 折算为不可变工件元数据。 */
    private LocalEmbeddingArtifact toArtifact(JsonNode descriptor, String descriptorHash) {
        return new LocalEmbeddingArtifact(requiredText(descriptor, "modelId"), descriptorHash,
                descriptor.path("dimension").asInt(), descriptor.path("maxTokens").asInt(),
                requiredText(descriptor, "queryInstruction"),
                descriptor.path("intraOpNumThreads").asInt(),
                descriptor.path("interOpNumThreads").asInt());
    }

    /** 读取必填文本字段；缺失或空白视为描述符非法。 */
    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
        }
        return value;
    }

    /** 流式计算文件的 SHA-256（固定 8KB 缓冲），返回带 {@code sha256:} 前缀。 */
    private String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    /** 一次性计算字节数组的 SHA-256，返回带 {@code sha256:} 前缀。 */
    private String sha256(byte[] bytes) {
        return "sha256:" + HexFormat.of().formatHex(digest().digest(bytes));
    }

    /** 获取 SHA-256 摘要实例；平台缺失时视为不可恢复的运行时错误。 */
    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LocalEmbeddingFailureException failure(String code) {
        return new LocalEmbeddingFailureException(code);
    }
}
