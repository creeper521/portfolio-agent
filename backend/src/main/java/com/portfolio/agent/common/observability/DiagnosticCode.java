package com.portfolio.agent.common.observability;

/**
 * 诊断错误码契约：为 diagnostic 事件中的 failure.code 等字段提供稳定的机器可读标识。
 *
 * <p>实现方（如各枚举）以枚举名作为 code 返回值，保证同一故障类别的标识跨版本稳定不变，
 * 便于运维侧按 code 聚合统计，而不依赖消息文案。</p>
 */
public interface DiagnosticCode {

    /**
     * 返回稳定的诊断错误码字符串，与实现枚举的常量名一致，永不改变。
     */
    String code();
}
