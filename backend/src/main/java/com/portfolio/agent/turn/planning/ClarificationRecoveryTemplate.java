package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
