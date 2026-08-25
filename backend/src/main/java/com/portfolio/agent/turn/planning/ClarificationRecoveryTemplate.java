package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 澄清恢复模板：澄清期间可持久化的最小恢复凭据的封闭接口。
 *
 * <p>Jackson 以 templateKind 判别子类型；实现只携带闭合枚举、公开 ID 与
 * 短句柄，不携带访客原文或 Provider 输出。</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "templateKind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BlockedGoalTemplate.class, name = "BLOCKED_GOAL"),
        @JsonSubTypes.Type(value = DiscussionSelectionTemplate.class, name = "DISCUSSION_SELECTION"),
        @JsonSubTypes.Type(value = DiscussionClarificationTemplate.class,
                name = "DISCUSSION_CLARIFICATION")
})
public sealed interface ClarificationRecoveryTemplate
        permits BlockedGoalTemplate, DiscussionSelectionTemplate,
        DiscussionClarificationTemplate { }
