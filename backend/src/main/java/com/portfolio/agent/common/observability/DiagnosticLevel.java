package com.portfolio.agent.common.observability;

/**
 * 诊断事件级别：与常规日志级别对齐的四级分类，供 {@link DiagnosticEvent} 标记事件严重程度。
 *
 * <p>DEBUG/INFO 用于常规运行轨迹，WARN 用于降级与回退，ERROR 用于启动失败或请求处理失败。</p>
 */
public enum DiagnosticLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
