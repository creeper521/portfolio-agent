package com.portfolio.agent.infrastructure.model.provider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 单个已配置模型的不可变、非秘密执行元数据：第二重 Provider 准入通过后
 * 由 {@code ConfiguredModelCatalog} 生成的目录描述符。
 *
 * <p>构造期完成全部校验并冻结指纹：selectionVersion 必须是长度不超过 128
 * 的受控字符格式；endpoint 必须是不含 userInfo/query/fragment 的 HTTPS URI；
 * 能力集非空且不含 null；输出预算必须为正且不超过上下文预算。
 * 指纹（{@link #getDescriptorFingerprint()}）由全部执行相关字段派生，
 * 任何字段变化都会得到不同指纹，用于快照一致性比对。
 */
public final class ModelProviderDescriptor {

    private static final Pattern SELECTION_VERSION_FORMAT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final ModelRef modelRef;
    private final String selectionVersion;
    private final String displayName;
    private final int displayOrder;
    private final URI endpoint;
    private final String modelName;
    private final ModelProviderProtocolProfile protocolProfile;
    private final Set<ModelCapability> capabilities;
    private final int contextWindowBudget;
    private final int outputBudget;
    private final String descriptorFingerprint;

    /**
     * 全参构造：校验并冻结全部字段与指纹。
     *
     * @throws IllegalArgumentException 任一字段非法（版本格式、文本为空、
     *         endpoint 非 HTTPS、能力集为空、预算越界）时抛出
     */
    public ModelProviderDescriptor(
            ModelRef modelRef,
            String selectionVersion,
            String displayName,
            int displayOrder,
            URI endpoint,
            String modelName,
            ModelProviderProtocolProfile protocolProfile,
            Set<ModelCapability> capabilities,
            int maxContextTokens,
            int maxOutputTokens) {
        this.modelRef = Objects.requireNonNull(modelRef, "modelRef");
        this.selectionVersion = requireSelectionVersion(selectionVersion);
        this.displayName = requireText(displayName, "displayName");
        this.displayOrder = displayOrder;
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.modelName = requireText(modelName, "modelName");
        this.protocolProfile = Objects.requireNonNull(protocolProfile, "protocolProfile");
        this.capabilities = copyCapabilities(capabilities);
        if (maxContextTokens < 1) {
            throw new IllegalArgumentException("maxContextTokens must be positive");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > maxContextTokens) {
            throw new IllegalArgumentException("maxOutputTokens is invalid");
        }
        this.contextWindowBudget = maxContextTokens;
        this.outputBudget = maxOutputTokens;
        descriptorFingerprint = fingerprint(
                modelRef.value(), selectionVersion, endpoint.toASCIIString(), modelName,
                protocolProfile.name(), canonicalCapabilities(capabilities),
                Integer.toString(maxContextTokens), Integer.toString(maxOutputTokens));
    }

    public ModelRef getModelRef() { return modelRef; }
    public String getSelectionVersion() { return selectionVersion; }
    public String getDisplayName() { return displayName; }
    public int getDisplayOrder() { return displayOrder; }
    public URI getEndpoint() { return endpoint; }
    public String getModelName() { return modelName; }
    public ModelProviderProtocolProfile getProtocolProfile() { return protocolProfile; }
    public Set<ModelCapability> getCapabilities() { return capabilities; }
    public int getMaxContextTokens() { return contextWindowBudget; }
    public int getMaxOutputTokens() { return outputBudget; }
    public String getDescriptorFingerprint() { return descriptorFingerprint; }

    /** 投影出免秘密的公开目录条目（不含 endpoint 与模型名）。 */
    public ModelCatalogEntry publicEntry() {
        return new ModelCatalogEntry(
                modelRef.value(), displayName, displayOrder,
                selectionVersion, capabilities);
    }

    /** 校验非空、非空白文本并去除首尾空白。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    /**
     * 校验 endpoint 是"干净"的 HTTPS URI（拒绝 userInfo、query、fragment），
     * 防止描述符夹带凭证或旁路参数。
     */
    private static URI requireHttpsEndpoint(URI value) {
        if (value == null
                || value.getScheme() == null
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must be an HTTPS URI with a host");
        }
        return value;
    }

    /** 校验 selectionVersion：非空、长度不超过 128 且符合受控字符格式。 */
    private static String requireSelectionVersion(String value) {
        if (value == null
                || value.length() > 128
                || !SELECTION_VERSION_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("selectionVersion format is invalid");
        }
        return value;
    }

    /** 校验能力集非空且不含 null，并返回防御性拷贝。 */
    private static Set<ModelCapability> copyCapabilities(Set<ModelCapability> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        for (ModelCapability value : values) {
            if (value == null) {
                throw new IllegalArgumentException("capabilities must not contain null");
            }
        }
        return Set.copyOf(values);
    }

    /** 生成能力集的规范字符串：按枚举名排序后以逗号连接，保证指纹输入稳定。 */
    public static String canonicalCapabilities(Set<ModelCapability> values) {
        return values.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * 对若干字符串计算 SHA-256 指纹：每段先写入 4 字节长度前缀再写内容，
     * 长度前缀防止不同分段方式拼出相同输入（如 "ab"+"c" 与 "a"+"bc"）。
     *
     * @throws IllegalStateException 平台缺少 SHA-256（理论上不可能）
     */
    public static String fingerprint(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
