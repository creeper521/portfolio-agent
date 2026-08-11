package com.portfolio.agent.answer.dto.response;

import java.util.List;
import java.util.Objects;

/** Safe, structured clarification without an internal clarification or task identifier. */
public final class ClarificationResponse {

    private final String scope;
    private final String promptCode;
    private final String prompt;
    private final List<Field> fields;
    private final int blockedTaskCount;
    private final int continuingTaskCount;

    public ClarificationResponse(
            String scope,
            String promptCode,
            String prompt,
            List<Field> fields,
            int blockedTaskCount,
            int continuingTaskCount) {
        this.scope = requireText(scope, "scope");
        this.promptCode = requireText(promptCode, "promptCode");
        this.prompt = requireText(prompt, "prompt");
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        this.blockedTaskCount = blockedTaskCount;
        this.continuingTaskCount = continuingTaskCount;
    }

    public String getScope() { return scope; }
    public String getPromptCode() { return promptCode; }
    public String getPrompt() { return prompt; }
    public List<Field> getFields() { return fields; }
    public int getBlockedTaskCount() { return blockedTaskCount; }
    public int getContinuingTaskCount() { return continuingTaskCount; }

    public static final class Field {
        private final String fieldKey;
        private final String inputMode;
        private final List<Option> options;
        private final boolean required;
        private final List<String> affectedGoalLabels;

        public Field(String fieldKey, String inputMode, List<Option> options,
                     boolean required, List<String> affectedGoalLabels) {
            this.fieldKey = requireText(fieldKey, "fieldKey");
            this.inputMode = requireText(inputMode, "inputMode");
            this.options = List.copyOf(Objects.requireNonNull(options, "options"));
            this.required = required;
            this.affectedGoalLabels = List.copyOf(
                    Objects.requireNonNull(affectedGoalLabels, "affectedGoalLabels"));
        }

        public String getFieldKey() { return fieldKey; }
        public String getInputMode() { return inputMode; }
        public List<Option> getOptions() { return options; }
        public boolean isRequired() { return required; }
        public List<String> getAffectedGoalLabels() { return affectedGoalLabels; }
    }

    public static final class Option {
        private final String value;
        private final String label;

        public Option(String value, String label) {
            this.value = requireText(value, "value");
            this.label = requireText(label, "label");
        }

        public String getValue() { return value; }
        public String getLabel() { return label; }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
