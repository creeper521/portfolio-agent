package com.portfolio.agent.portfolio.domain;

/**
 * 项目状态：项目整体的交付状态，是对外口径的权威字段。
 *
 * <ul>
 *   <li>DELIVERED：已交付</li>
 *   <li>IN_PROGRESS：进行中</li>
 *   <li>PROTOTYPE：原型</li>
 *   <li>LEARNING_ONLY：仅用于学习</li>
 * </ul>
 *
 * <p>不得把原型或学习类项目表述为已交付成果。
 */
public enum ProjectStatus {
    DELIVERED,
    IN_PROGRESS,
    PROTOTYPE,
    LEARNING_ONLY
}
