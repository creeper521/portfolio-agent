package com.portfolio.agent.answer.dto.response;

import java.util.List;
import java.util.Objects;

/** Safe, structured clarification without an internal clarification or task identifier. */
public final class ClarificationResponse {

    private final String clarificationId;
    private final String scope;
    private final String promptCode;
    private final String prompt;
    private final List<Field> fields;
    private final int blockedTaskCount;
    private final int continuingTaskCount;
    private final List<String> continuingGoalLabels;
    private final List<BlockedGoal> blockedGoals;

    public ClarificationResponse(
            String scope,
            String promptCode,
            String prompt,
            List<Field> fields,
            int blockedTaskCount,
            int continuingTaskCount) {
        this(null, scope, promptCode, prompt, fields, blockedTaskCount, continuingTaskCount,
                List.of(), List.of());
    }

    public ClarificationResponse(
            String clarificationId,
            String scope,
            String promptCode,
            String prompt,
            List<Field> fields,
            int blockedTaskCount,
            int continuingTaskCount,
            List<String> continuingGoalLabels,
            List<BlockedGoal> blockedGoals) {
        this.clarificationId = optionalText(clarificationId);
        this.scope = requireText(scope, "scope");
        this.promptCode = requireText(promptCode, "promptCode");
        this.prompt = requireText(prompt, "prompt");
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        this.blockedTaskCount = blockedTaskCount;
        this.continuingTaskCount = continuingTaskCount;
        this.continuingGoalLabels = List.copyOf(
                Objects.requireNonNull(continuingGoalLabels, "continuingGoalLabels"));
        this.blockedGoals = List.copyOf(Objects.requireNonNull(blockedGoals, "blockedGoals"));
    }

    public String getClarificationId() { return clarificationId; }
    public String getScope() { return scope; }
    public String getPromptCode() { return promptCode; }
    public String getPrompt() { return prompt; }
    public List<Field> getFields() { return fields; }
    public int getBlockedTaskCount() { return blockedTaskCount; }
    public int getContinuingTaskCount() { return continuingTaskCount; }
    public List<String> getContinuingGoalLabels() { return continuingGoalLabels; }
    public List<BlockedGoal> getBlockedGoals() { return blockedGoals; }

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
        private final Resolution resolution;

        public Option(String value, String label) {
            this(value, label, null, null);
        }

        public Option(String value, String label, String subjectType, String subjectId) {
            this.value = requireText(value, "value");
            this.label = requireText(label, "label");
            String normalizedType = optionalText(subjectType);
            String normalizedId = optionalText(subjectId);
            this.resolution = normalizedType == null && normalizedId == null
                    ? null : new Resolution("SUBJECT_REFERENCE", normalizedType, normalizedId);
        }

        public String getValue() { return value; }
        public String getLabel() { return label; }
        public Resolution getResolution() { return resolution; }
    }

    public static final class Resolution {
        private final String kind;
        private final String subjectType;
        private final String subjectId;

        public Resolution(String kind, String subjectType, String subjectId) {
            this.kind = requireText(kind, "kind");
            this.subjectType = requireText(subjectType, "subjectType");
            this.subjectId = requireText(subjectId, "subjectId");
        }

        public String getKind() { return kind; }
        public String getSubjectType() { return subjectType; }
        public String getSubjectId() { return subjectId; }
    }

    public static final class BlockedGoal {
        private final String goalLabel;
        private final String reasonCode;

        public BlockedGoal(String goalLabel, String reasonCode) {
            this.goalLabel = requireText(goalLabel, "goalLabel");
            this.reasonCode = requireText(reasonCode, "reasonCode");
        }

        public String getGoalLabel() { return goalLabel; }
        public String getReasonCode() { return reasonCode; }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
