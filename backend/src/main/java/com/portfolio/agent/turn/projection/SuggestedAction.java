package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.turn.continuation.ContinuationReference;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SuggestedAction {
    private final String actionId;
    private final String label;
    private final String inputText;
    private final ContinuationReference continuation;
    public SuggestedAction(
            String actionId, String label, String inputText,
            ContinuationReference continuation) {
        this.actionId = text(actionId, "actionId");
        this.label = text(label, "label");
        this.inputText = inputText == null ? null : text(inputText, "inputText");
        this.continuation = continuation;
    }
    public String getActionId() { return actionId; }
    public String getLabel() { return label; }
    public String getInputText() { return inputText; }
    public ContinuationReference getContinuation() { return continuation; }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
