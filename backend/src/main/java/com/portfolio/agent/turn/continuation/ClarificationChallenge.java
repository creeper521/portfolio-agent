package com.portfolio.agent.turn.continuation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * 澄清挑战：面向访客的澄清表单（澄清 State 的公开侧表示）。
 *
 * <p>由澄清 ID、提问文案与恰好一组表单字段（单选或文本）组成；
 * affectedGoalIds 记录澄清影响的目标。内部答案绑定存放在
 * {@link ClarificationStore.Record}，本类型不携带任何内部绑定。</p>
 */
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
        this.affectedGoalIds = affectedGoalIds == null
                ? List.of() : List.copyOf(affectedGoalIds);
    }
    public String getClarificationId() { return clarificationId; }
    public String getPrompt() { return prompt; }
    public List<Field> getFields() { return fields; }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getAffectedGoalIds() { return affectedGoalIds; }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "kind", visible = false)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SingleChoiceField.class, name = "SINGLE_CHOICE"),
            @JsonSubTypes.Type(value = TextField.class, name = "TEXT")
    })
    /** 澄清字段封闭接口：单选或文本，Jackson 以 kind 判别子类型。 */
    public sealed interface Field permits SingleChoiceField, TextField {
        Kind getKind();
        String getFieldId();
        String getLabel();
        boolean isRequired();
        enum Kind { SINGLE_CHOICE, TEXT }
    }

    /** 单选字段：固定选项集合（非空）。 */
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

    /** 文本字段：长度上限 1..2000。 */
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

    /** 单选项：选项 ID 与展示标签。 */
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
