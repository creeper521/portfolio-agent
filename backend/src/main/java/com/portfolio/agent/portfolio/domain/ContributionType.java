package com.portfolio.agent.portfolio.domain;

/**
 * 贡献类型：档案主人在该项工作中的参与性质，是对外口径的权威字段。
 *
 * <ul>
 *   <li>INDEPENDENT：独立完成交付</li>
 *   <li>PRIMARY：主力承担</li>
 *   <li>COLLABORATIVE：协作参与</li>
 *   <li>OBSERVED_LEARNING：观察学习</li>
 * </ul>
 *
 * <p>任何表述都不得把协作、原型或观察类工作扩大为独立交付成果。
 */
public enum ContributionType {
    INDEPENDENT,
    PRIMARY,
    COLLABORATIVE,
    OBSERVED_LEARNING
}
