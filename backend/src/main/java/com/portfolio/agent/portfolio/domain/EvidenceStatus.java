package com.portfolio.agent.portfolio.domain;

/**
 * 证据的公开状态：治理流程对证据可否对外引用的结论。
 *
 * <ul>
 *   <li>APPROVED：已审校通过，是唯一允许进入对外响应的取值</li>
 *   <li>PENDING：尚在审校中，不得出现在公开快照</li>
 *   <li>REJECTED：审校未通过，不得出现在公开快照</li>
 * </ul>
 */
public enum EvidenceStatus {
    APPROVED,
    PENDING,
    REJECTED
}
