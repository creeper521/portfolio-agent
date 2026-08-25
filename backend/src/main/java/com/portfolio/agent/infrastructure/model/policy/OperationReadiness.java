package com.portfolio.agent.infrastructure.model.policy;
/** Operation 就绪度三态。 */
public enum OperationReadiness {
    /** 显式禁用或未配置：Operation 不可用且无需补救。 */
    DISABLED,
    /** 策略完备，可进入 Provider 侧准入与执行。 */
    AVAILABLE_WITH_PROVIDER,
    /** 声明为 ENABLED 但 schema/预算/超时不完整：启动即失败。 */
    INCOMPLETE_CONFIGURATION
}
