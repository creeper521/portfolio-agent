package com.portfolio.agent.portfolio.domain;

/**
 * 证据类型：证据凭证的物理形态。
 *
 * <ul>
 *   <li>COLLECTION：多来源汇总</li>
 *   <li>DOCUMENT：文档</li>
 *   <li>SCREENSHOT：截图</li>
 *   <li>CODE：代码</li>
 *   <li>TEST_RESULT：测试结果</li>
 * </ul>
 */
public enum EvidenceType {
    COLLECTION,
    DOCUMENT,
    SCREENSHOT,
    CODE,
    TEST_RESULT
}
