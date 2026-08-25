package com.portfolio.agent.infrastructure.model;

/**
 * 模型执行解析失败异常：Turn 显式选择的模型无法解析为可执行快照。
 *
 * <p>该异常是封闭、provider 中立的：只携带稳定的 {@link Code}，
 * 绝不携带 Provider 细节（endpoint、模型名、凭证等），因此可以安全地
 * 折算进公开失败语义。
 */
public final class ModelExecutionResolutionException extends RuntimeException {
    private final Code code;

    /**
     * 以失败码构造异常；消息固定为中性描述，不拼接任何选择或目录细节。
     *
     * @param code 解析失败码，不允许为 null
     */
    public ModelExecutionResolutionException(Code code) {
        super("model execution selection cannot be resolved");
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    /** 封闭的解析失败码。 */
    public enum Code {
        /** 所选 modelRef 在冻结目录或传输绑定中不存在。 */
        SELECTED_MODEL_UNAVAILABLE,
        /** 请求携带的 selectionVersion 与目录当前版本不一致。 */
        MODEL_SELECTION_STALE
    }
}
