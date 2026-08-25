package com.portfolio.agent.portfolio.domain;

/**
 * 预设问题契约状态：预设问题的治理生命周期。
 *
 * <ul>
 *   <li>DRAFT：草稿（缺省值），尚未对外生效，不得声明 Claim 契约</li>
 *   <li>ACTIVE：生效中，对外输出并参与契约集哈希</li>
 *   <li>SUSPENDED：暂停，暂时不对外输出</li>
 *   <li>RETIRED：退役，不再使用</li>
 * </ul>
 */
public enum PresetContractStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    RETIRED
}
