package com.portfolio.agent.common.observability;

/**
 * 诊断事件发布器契约：把已通过 {@link DiagnosticEvent} 白名单校验的结构化事件交给底层实现输出。
 *
 * <p>典型实现为 SLF4J 结构化日志发布器；调用方（启动诊断、请求诊断、前端诊断等）通过它
 * 记录运行状态而不直接依赖具体日志框架。实现必须不抛出影响业务流程的异常，
 * 且不得在发布过程中补充事件白名单之外的字段。</p>
 */
public interface DiagnosticEventPublisher {

    /**
     * 发布一条诊断事件。
     *
     * @param event 已完成字段白名单校验的事件，不允许为 null
     */
    void publish(DiagnosticEvent event);
}
