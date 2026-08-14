package com.portfolio.agent.answer.adapter.model;

public enum ProviderOperation {
    CLASSIFY("intent"),
    SEMANTIC_ROUTE("semantic_route"),
    GENERATE("generation"),
    GENERAL_ANSWER_MATERIAL("general_answer_material"),
    CROSS_DOMAIN_EXPRESSION("cross_domain_expression"),
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
