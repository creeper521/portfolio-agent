package com.portfolio.agent.answer.adapter.model;

public enum ProviderOperation {
    CLASSIFY("intent"),
    CLASSIFY_PORTFOLIO_TASK("portfolio_task"),
    PLAN_TOOLS("tool_plan"),
    GENERATE("generation"),
    REVIEW("review"),
    SUGGEST("suggestion"),
    SUMMARIZE("summary"),
    EXPRESS("expression");

    private final String promptOperation;

    ProviderOperation(String promptOperation) {
        this.promptOperation = promptOperation;
    }

    public String getPromptOperation() {
        return promptOperation;
    }
}
