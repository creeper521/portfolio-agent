package com.portfolio.agent.portfolio.domain;

/**
 * 断言验证状态：断言当前被证据支撑到的程度。
 *
 * <ul>
 *   <li>VERIFIED：已验证（要求 verificationBasis 为 EVIDENCE_SUPPORTED 且有 DIRECT 证据）</li>
 *   <li>PARTIALLY_VERIFIED：部分验证</li>
 *   <li>UNVERIFIED：未验证</li>
 * </ul>
 */
public enum ClaimVerificationStatus {
    VERIFIED,
    PARTIALLY_VERIFIED,
    UNVERIFIED
}
