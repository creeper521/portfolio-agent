package com.portfolio.agent.portfolio.domain;

/**
 * 支撑类型：断言—证据关联中证据对断言的支撑强度。
 *
 * <ul>
 *   <li>DIRECT：直接证明（成果类断言至少需要一条）</li>
 *   <li>CORROBORATING：佐证</li>
 *   <li>CONTEXTUAL：背景参考</li>
 * </ul>
 */
public enum SupportType {
    DIRECT,
    CORROBORATING,
    CONTEXTUAL
}
