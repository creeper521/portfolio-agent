package com.portfolio.agent.portfolio.domain;

/**
 * 职业赛道：项目所服务的求职方向分类。
 *
 * <ul>
 *   <li>JAVA_BACKEND：Java 后端方向</li>
 *   <li>AGENT：Agent 方向</li>
 *   <li>UNCLASSIFIED：未分类（schema 4.0 起快照校验不允许项目停留在该值）</li>
 * </ul>
 */
public enum CareerTrack {
    JAVA_BACKEND,
    AGENT,
    UNCLASSIFIED
}
