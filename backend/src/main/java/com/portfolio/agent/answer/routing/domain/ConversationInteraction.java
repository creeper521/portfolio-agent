package com.portfolio.agent.answer.routing.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Public-safe, fact-free conversational recovery produced from a closed server template. */
public final class ConversationInteraction {

    private final Act act;
    private final String message;
    private final List<String> suggestedActionIds;

    public ConversationInteraction(
            Act act, String message, List<String> suggestedActionIds) {
        this.act = Objects.requireNonNull(act, "act");
        if (message == null || message.isBlank() || message.length() > 240) {
            throw new IllegalArgumentException("conversation message is invalid");
        }
        this.message = message.trim();
        List<String> actions = List.copyOf(Objects.requireNonNull(
                suggestedActionIds, "suggestedActionIds"));
        if (actions.size() > 3 || new LinkedHashSet<>(actions).size() != actions.size()
                || actions.stream().anyMatch(value -> value == null
                || !value.matches("[A-Za-z0-9._-]{1,64}"))) {
            throw new IllegalArgumentException("suggested action ids are invalid");
        }
        this.suggestedActionIds = actions;
    }

    public Act getAct() { return act; }
    public String getMessage() { return message; }
    public List<String> getSuggestedActionIds() { return suggestedActionIds; }

    public enum Act {
        SOCIAL_ACKNOWLEDGEMENT,
        CAPABILITY_ORIENTATION,
        RECOVERY_PROMPT
    }
}
