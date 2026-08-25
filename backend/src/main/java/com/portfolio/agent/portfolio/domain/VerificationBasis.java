package com.portfolio.agent.portfolio.domain;

/**
 * 验证依据：断言结论赖以成立的依据来源，约束 verificationStatus 的上限。
 *
 * <ul>
 *   <li>EVIDENCE_SUPPORTED：有证据支撑（才允许 VERIFIED）</li>
 *   <li>SELF_DECLARED：自我声明（不得为 VERIFIED）</li>
 *   <li>INFERRED：推断得出（不得为 VERIFIED）</li>
 *   <li>UNSUPPORTED：无依据（必须为 UNVERIFIED）</li>
 * </ul>
 */
public enum VerificationBasis {
    EVIDENCE_SUPPORTED,
    SELF_DECLARED,
    INFERRED,
    UNSUPPORTED
}
