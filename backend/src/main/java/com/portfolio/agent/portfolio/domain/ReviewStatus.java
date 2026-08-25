package com.portfolio.agent.portfolio.domain;

/**
 * 审校结论：治理流程对关联关系的复核结果。
 *
 * <ul>
 *   <li>APPROVED：审校通过，只有该取值允许进入公开快照</li>
 *   <li>REJECTED：审校拒绝</li>
 * </ul>
 */
public enum ReviewStatus {
    APPROVED,
    REJECTED
}
