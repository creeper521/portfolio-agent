package com.portfolio.agent.portfolio.domain;

/**
 * 断言主体类型：与 {@link Claim#getSubjectId} 配合定位断言陈述的对象。
 *
 * <ul>
 *   <li>OWNER：档案主人本人</li>
 *   <li>PROJECT：某个项目（subjectId 为项目 id）</li>
 *   <li>CASE：某个案例（subjectId 为案例 id）</li>
 *   <li>INTERNSHIP：实习经历整体</li>
 * </ul>
 */
public enum ClaimSubjectType {
    OWNER,
    PROJECT,
    CASE,
    INTERNSHIP
}
