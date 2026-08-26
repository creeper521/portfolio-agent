package com.portfolio.agent.infrastructure.model.provider;

import java.util.Map;

/**
 * Provider 协议画像封闭枚举：结构化传输获准使用的请求形态。
 *
 * <p>仅两个经过批准的封闭画像（GLM/Zhipu 与 Qwen/DashScope 的
 * chat-completions 变体），未列出的协议一律拒绝，杜绝开放式协议扩展。
 * 每个画像只负责冻结传输级字段（stream 与各自的 thinking 关闭参数）；
 * 结构化输出载体由 {@code OperationBinding} 的 strategy 编译，不能在此重复决定。
 */
public enum ModelProviderProtocolProfile {
    /** 智谱 GLM chat-completions 画像：以 {@code thinking.type=disabled} 关闭思考。 */
    ZHIPU_CHAT_COMPLETIONS("zhipu-chat-completions-v1") {
        @Override
        public void applyStructuredOutputFields(Map<String, Object> payload) {
            common(payload);
            payload.put("thinking", Map.of("type", "disabled"));
        }
    },
    /** 阿里 DashScope（Qwen）chat-completions 画像：以 {@code enable_thinking=false} 关闭思考。 */
    DASHSCOPE_CHAT_COMPLETIONS("dashscope-chat-completions-v1") {
        @Override
        public void applyStructuredOutputFields(Map<String, Object> payload) {
            common(payload);
            payload.put("enable_thinking", false);
        }
    };

    private final String version;

    ModelProviderProtocolProfile(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    /** 把本画像的传输级字段注入请求负载（公共字段 + 画像私有字段）。 */
    public abstract void applyStructuredOutputFields(Map<String, Object> payload);

    /**
     * 从配置名解析画像：只接受两个枚举名，其他取值（含空白）一律拒绝。
     *
     * @throws IllegalArgumentException 配置名为 null 或未获批准时抛出；
     *         异常消息只含 BLANK/UNKNOWN 分类，不回显原始取值
     */
    public static ModelProviderProtocolProfile fromConfiguredName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("protocol profile is required");
        }
        return switch (value) {
            case "ZHIPU_CHAT_COMPLETIONS" -> ZHIPU_CHAT_COMPLETIONS;
            case "DASHSCOPE_CHAT_COMPLETIONS" -> DASHSCOPE_CHAT_COMPLETIONS;
            default -> throw new IllegalArgumentException(
                    "protocol profile is not approved: " + safeCategory(value));
        };
    }

    /** 所有画像共用的公共字段：仅冻结非流式传输；结构策略由 OperationBinding 编译。 */
    private static void common(Map<String, Object> payload) {
        payload.put("stream", false);
    }

    /** 配置名非法时的安全分类：空白返回 BLANK，其余返回 UNKNOWN。 */
    private static String safeCategory(String value) {
        return value.isBlank() ? "BLANK" : "UNKNOWN";
    }
}
