package com.portfolio.agent.answer.routing.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public-safe clarification payload. It carries goal labels, never internal task identifiers. */
public final class ClarificationRequest {

    private final String clarificationId;
    private final Scope scope;
    private final String promptCode;
    private final String prompt;
    private final List<Field> fields;
    private final int blockedTaskCount;
    private final int continuingTaskCount;
    private final List<String> continuingGoalLabels;
    private final List<BlockedGoal> blockedGoals;

    public ClarificationRequest(
            Scope scope,
            String promptCode,
            String prompt,
            List<Field> fields,
            int blockedTaskCount,
            int continuingTaskCount) {
        this(newClarificationId(), scope, promptCode, prompt, fields, blockedTaskCount, continuingTaskCount);
    }

    public ClarificationRequest(
            String clarificationId,
            Scope scope,
            String promptCode,
            String prompt,
            List<Field> fields,
            int blockedTaskCount,
            int continuingTaskCount) {
        this(clarificationId, scope, promptCode, prompt, fields, blockedTaskCount, continuingTaskCount,
                List.of(), List.of());
    }

    public ClarificationRequest(
            Scope scope,
            String promptCode,
            String prompt,
            List<Field> fields,
            int blockedTaskCount,
            int continuingTaskCount,
            List<String> continuingGoalLabels,
            List<BlockedGoal> blockedGoals) {
        this(newClarificationId(), scope, promptCode, prompt, fields, blockedTaskCount, continuingTaskCount,
                continuingGoalLabels, blockedGoals);
    }

    public ClarificationRequest(
            String clarificationId,
            Scope scope,
            String promptCode,
            String prompt,
            List<Field> fields,
            int blockedTaskCount,
            int continuingTaskCount,
            List<String> continuingGoalLabels,
            List<BlockedGoal> blockedGoals) {
        this.clarificationId = requireClarificationId(clarificationId);
        this.scope = Objects.requireNonNull(scope, "scope");
        this.promptCode = requireCode(promptCode);
        this.prompt = requireText(prompt, "prompt");
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (blockedTaskCount < 1 || continuingTaskCount < 0) {
            throw new IllegalArgumentException("clarification task counts are invalid");
        }
        if (scope == Scope.CRITICAL && continuingTaskCount != 0) {
            throw new IllegalArgumentException("critical clarification cannot continue tasks");
        }
        this.blockedTaskCount = blockedTaskCount;
        this.continuingTaskCount = continuingTaskCount;
        this.continuingGoalLabels = copyTexts(continuingGoalLabels, "continuingGoalLabels");
        this.blockedGoals = List.copyOf(Objects.requireNonNull(blockedGoals, "blockedGoals"));
        if (this.continuingGoalLabels.size() > continuingTaskCount) {
            throw new IllegalArgumentException("continuing goal labels must not exceed continuing task count");
        }
        if (this.blockedGoals.size() > blockedTaskCount) {
            throw new IllegalArgumentException("blocked goals must not exceed blocked task count");
        }
    }

    static ClarificationRequest splitRequired(int requestedTaskCount) {
        return new ClarificationRequest(
                Scope.CRITICAL, "ROUTING_TASK_SPLIT_REQUIRED", "请将目标拆分为不超过六项的独立请求。",
                List.of(new Field("taskSplit", InputMode.SHORT_TEXT, List.of(), true, List.of("拆分当前请求"))),
                requestedTaskCount, 0);
    }

    static ClarificationRequest comparisonSubjects(
            Scope scope, int continuingTaskCount, List<Option> options,
            List<String> continuingGoalLabels) {
        return new ClarificationRequest(
                scope, "ROUTING_COMPARISON_SUBJECT_MISSING", "请补充需要比较的另一项公开项目。",
                List.of(new Field("comparisonSubject", InputMode.SINGLE_CHOICE, options, true,
                        List.of("比较公开项目"))),
                1, continuingTaskCount, continuingGoalLabels,
                List.of(new BlockedGoal("比较公开项目", "WAITING_FOR_COMPARISON_SUBJECT")));
    }

    static ClarificationRequest contextConflict(List<Option> options) {
        return new ClarificationRequest(
                Scope.CRITICAL, "ROUTING_SUBJECT_CLARIFICATION_REQUIRED", "请明确本轮要处理的公开项目。",
                List.of(new Field("subject", InputMode.SINGLE_CHOICE, options, true, List.of("继续当前请求"))),
                1, 0, List.of(),
                List.of(new BlockedGoal("继续当前请求", "WAITING_FOR_SUBJECT")));
    }

    public String getClarificationId() { return clarificationId; }
    public Scope getScope() { return scope; }
    public String getPromptCode() { return promptCode; }
    public String getPrompt() { return prompt; }
    public List<Field> getFields() { return fields; }
    public int getBlockedTaskCount() { return blockedTaskCount; }
    public int getContinuingTaskCount() { return continuingTaskCount; }
    public List<String> getContinuingGoalLabels() { return continuingGoalLabels; }
    public List<BlockedGoal> getBlockedGoals() { return blockedGoals; }

    @Override
    public String toString() {
        return "ClarificationRequest{scope=" + scope + ", promptCode=" + promptCode
                + ", fieldCount=" + fields.size() + ", blockedTaskCount=" + blockedTaskCount
                + ", continuingTaskCount=" + continuingTaskCount + '}';
    }

    public enum Scope { LOCAL, CRITICAL }
    public enum InputMode { SINGLE_CHOICE, MULTI_CHOICE, SHORT_TEXT }

    public static final class Field {
        private final String fieldKey;
        private final InputMode inputMode;
        private final List<Option> options;
        private final boolean required;
        private final List<String> affectedGoalLabels;

        public Field(
                String fieldKey, InputMode inputMode, List<Option> options,
                boolean required, List<String> affectedGoalLabels) {
            this.fieldKey = requireText(fieldKey, "fieldKey");
            this.inputMode = Objects.requireNonNull(inputMode, "inputMode");
            this.options = List.copyOf(Objects.requireNonNull(options, "options"));
            this.required = required;
            this.affectedGoalLabels = List.copyOf(Objects.requireNonNull(affectedGoalLabels, "affectedGoalLabels"));
        }

        public String getFieldKey() { return fieldKey; }
        public InputMode getInputMode() { return inputMode; }
        public List<Option> getOptions() { return options; }
        public boolean isRequired() { return required; }
        public List<String> getAffectedGoalLabels() { return affectedGoalLabels; }
    }

    public static final class Option {
        private final String value;
        private final String label;
        private final String subjectType;
        private final String subjectId;

        public Option(String value, String label) {
            this(value, label, null, null);
        }

        public Option(String value, String label, String subjectType, String subjectId) {
            this.value = requireText(value, "value");
            this.label = requireText(label, "label");
            this.subjectType = optionalText(subjectType);
            this.subjectId = optionalText(subjectId);
            if ((this.subjectType == null) != (this.subjectId == null)) {
                throw new IllegalArgumentException("subjectType and subjectId must be provided together");
            }
        }

        public String getValue() { return value; }
        public String getLabel() { return label; }
        public String getSubjectType() { return subjectType; }
        public String getSubjectId() { return subjectId; }
    }

    public static final class BlockedGoal {
        private final String goalLabel;
        private final String reasonCode;

        public BlockedGoal(String goalLabel, String reasonCode) {
            this.goalLabel = requireText(goalLabel, "goalLabel");
            this.reasonCode = requireCode(reasonCode);
        }

        public String getGoalLabel() { return goalLabel; }
        public String getReasonCode() { return reasonCode; }
    }

    private static String requireCode(String value) {
        String normalized = requireText(value, "promptCode");
        if (!normalized.matches("[A-Z]+_[A-Z0-9_]+")) {
            throw new IllegalArgumentException("promptCode must be an uppercase public code");
        }
        return normalized;
    }

    private static String newClarificationId() {
        return "clarify-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String requireClarificationId(String value) {
        String normalized = requireText(value, "clarificationId");
        if (!normalized.matches("clarify-[a-f0-9]{32}") || normalized.contains("task-")) {
            throw new IllegalArgumentException("clarificationId must be opaque and non-task-scoped");
        }
        return normalized;
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

    private static List<String> copyTexts(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        return values.stream().map(value -> requireText(value, name)).toList();
    }
}
