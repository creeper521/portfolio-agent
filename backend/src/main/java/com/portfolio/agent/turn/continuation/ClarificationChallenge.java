package com.portfolio.agent.turn.continuation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ClarificationChallenge {
    private final String clarificationId;
    private final String prompt;
    private final List<Field> fields;
    private final List<String> affectedGoalIds;

    public ClarificationChallenge(
            String clarificationId, String prompt,
            List<Field> fields, List<String> affectedGoalIds) {
        this.clarificationId = text(clarificationId, "clarificationId");
        this.prompt = text(prompt, "prompt");
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (this.fields.isEmpty()) throw new IllegalArgumentException("fields are required");
        this.affectedGoalIds = List.copyOf(
                Objects.requireNonNull(affectedGoalIds, "affectedGoalIds"));
    }
    public String getClarificationId() { return clarificationId; }
    public String getPrompt() { return prompt; }
    public List<Field> getFields() { return fields; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getAffectedGoalIds() { return affectedGoalIds; }

    public sealed interface Field permits SingleChoiceField, TextField {
        Kind getKind();
        String getFieldId();
        String getLabel();
        boolean isRequired();
        enum Kind { SINGLE_CHOICE, TEXT }
    }

    public static final class SingleChoiceField implements Field {
        private final String fieldId;
        private final String label;
        private final boolean required;
        private final List<Choice> choices;
        public SingleChoiceField(String fieldId, String label, boolean required, List<Choice> choices) {
            this.fieldId = text(fieldId, "fieldId");
            this.label = text(label, "label");
            this.required = required;
            this.choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
            if (this.choices.isEmpty()) throw new IllegalArgumentException("choices are required");
        }
        @Override public Kind getKind() { return Kind.SINGLE_CHOICE; }
        @Override public String getFieldId() { return fieldId; }
        @Override public String getLabel() { return label; }
        @Override public boolean isRequired() { return required; }
        public List<Choice> getChoices() { return choices; }
    }

    public static final class TextField implements Field {
        private final String fieldId;
        private final String label;
        private final boolean required;
        private final int limit;
        public TextField(String fieldId, String label, boolean required, int limit) {
            this.fieldId = text(fieldId, "fieldId");
            this.label = text(label, "label");
            this.required = required;
            if (limit < 1 || limit > 2000) throw new IllegalArgumentException("limit is invalid");
            this.limit = limit;
        }
        @Override public Kind getKind() { return Kind.TEXT; }
        @Override public String getFieldId() { return fieldId; }
        @Override public String getLabel() { return label; }
        @Override public boolean isRequired() { return required; }
        public int getLimit() { return limit; }
    }

    public record Choice(String choiceId, String label) {
        public Choice {
            choiceId = text(choiceId, "choiceId");
            label = text(label, "label");
        }
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
