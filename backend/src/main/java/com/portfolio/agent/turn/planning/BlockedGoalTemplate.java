package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 澄清期间可持久化的最小目标模板。
 *
 * <p>这里只保存闭合枚举、公开主体 ID、数字和闭合约束；不保存用户原问题、
 * InputAnchor、ConversationWindow、Prompt 或模型原始输出。恢复完整目标时使用
 * 服务端固定语义锚点，避免为了续接而长期保留访客原文。</p>
 */
public final class BlockedGoalTemplate {
    private static final String RESTORED_STATEMENT = "已澄清的公开目标";

    private final GoalKind goalKind;
    private final List<Subject> subjects;
    private final Set<GoalRequestedOutput> requestedOutputs;
    private final Set<UserGoalProposal.Facet> facets;
    private final Set<String> dimensions;
    private final Integer requestedSize;
    private final Set<String> constraints;
    private final ClarificationProposal.Field unresolvedField;
    private final Set<ClarificationProposal.Field> askedFields;
    private final List<ClarificationProposal.Field> remainingFields;
    private final int depth;

    @JsonCreator
    public BlockedGoalTemplate(
            @JsonProperty(value = "goalKind", required = true) GoalKind goalKind,
            @JsonProperty(value = "subjects", required = true) List<Subject> subjects,
            @JsonProperty(value = "requestedOutputs", required = true) Set<GoalRequestedOutput> requestedOutputs,
            @JsonProperty(value = "facets", required = true) Set<UserGoalProposal.Facet> facets,
            @JsonProperty(value = "dimensions", required = true) Set<String> dimensions,
            @JsonProperty(value = "requestedSize", required = true) Integer requestedSize,
            @JsonProperty(value = "constraints", required = true) Set<String> constraints,
            @JsonProperty(value = "unresolvedField", required = true) ClarificationProposal.Field unresolvedField,
            @JsonProperty(value = "askedFields", required = true) Set<ClarificationProposal.Field> askedFields,
            @JsonProperty(value = "remainingFields", required = true) List<ClarificationProposal.Field> remainingFields,
            @JsonProperty(value = "depth", required = true) int depth) {
        this.goalKind = Objects.requireNonNull(goalKind, "goalKind");
        if (goalKind != GoalKind.PORTFOLIO_FACT
                && goalKind != GoalKind.PORTFOLIO_COMPARE
                && goalKind != GoalKind.PORTFOLIO_RECOMMEND) {
            throw new IllegalArgumentException("raw-anchor goal cannot be persisted for clarification");
        }
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.requestedOutputs = Set.copyOf(
                Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
        this.facets = Set.copyOf(facets == null ? Set.of() : facets);
        this.dimensions = closedNames(dimensions, "dimensions", true);
        this.requestedSize = requestedSize;
        this.constraints = closedNames(constraints, "constraints", true);
        this.unresolvedField = Objects.requireNonNull(unresolvedField, "unresolvedField");
        this.askedFields = Set.copyOf(
                Objects.requireNonNull(askedFields, "askedFields"));
        this.remainingFields = List.copyOf(
                Objects.requireNonNull(remainingFields, "remainingFields"));
        if (!this.askedFields.contains(unresolvedField)) {
            throw new IllegalArgumentException("current field must be recorded as asked");
        }
        if (depth < 1 || depth > 2 || depth != this.askedFields.size()) {
            throw new IllegalArgumentException("clarification depth is invalid");
        }
        if (this.remainingFields.size() > 1
                || this.remainingFields.stream().distinct().count() != this.remainingFields.size()
                || this.remainingFields.stream().anyMatch(this.askedFields::contains)) {
            throw new IllegalArgumentException("remaining clarification fields are invalid");
        }
        if (depth + this.remainingFields.size() > 2) {
            throw new IllegalArgumentException("at most two clarification fields are allowed");
        }
        this.depth = depth;
        validatePartialShape();
    }

    public static BlockedGoalTemplate recommendation(
            Integer requestedSize,
            Set<String> constraints,
            ClarificationProposal.Field unresolvedField) {
        return new BlockedGoalTemplate(
                GoalKind.PORTFOLIO_RECOMMEND,
                List.of(), Set.of(GoalRequestedOutput.RECOMMENDATION),
                Set.of(), Set.of(), requestedSize, constraints,
                unresolvedField, Set.of(unresolvedField), List.of(), 1);
    }

    public BlockedGoalTemplate(
            GoalKind goalKind,
            List<Subject> subjects,
            Set<GoalRequestedOutput> requestedOutputs,
            Set<UserGoalProposal.Facet> facets,
            Set<String> dimensions,
            Integer requestedSize,
            Set<String> constraints,
            ClarificationProposal.Field unresolvedField,
            Set<ClarificationProposal.Field> askedFields,
            int depth) {
        this(goalKind, subjects, requestedOutputs, facets, dimensions, requestedSize,
                constraints, unresolvedField, askedFields, List.of(), depth);
    }

    public Resolution resolve(ResolutionValue value) {
        if (value == null || value.field() != unresolvedField) {
            return Resolution.noInformation();
        }
        List<Subject> nextSubjects = subjects;
        Set<GoalRequestedOutput> nextOutputs = requestedOutputs;
        Integer nextSize = requestedSize;
        Set<String> nextConstraints = constraints;
        if (value instanceof SubjectValue subjectValue) {
            nextSubjects = mergeSubjects(subjects, subjectValue.subjects());
        } else if (value instanceof RequestedSizeValue sizeValue) {
            nextSize = sizeValue.requestedSize();
        } else if (value instanceof OutputValue outputValue) {
            nextOutputs = outputValue.outputs();
        } else if (value instanceof ConstraintValue constraintValue) {
            nextConstraints = constraintValue.constraints();
        } else {
            return Resolution.noInformation();
        }
        try {
            if (!remainingFields.isEmpty()) {
                ClarificationProposal.Field nextField = remainingFields.getFirst();
                Set<ClarificationProposal.Field> nextAsked =
                        new java.util.LinkedHashSet<>(askedFields);
                nextAsked.add(nextField);
                BlockedGoalTemplate continuation = new BlockedGoalTemplate(
                        goalKind, nextSubjects, nextOutputs, facets, dimensions,
                        nextSize, nextConstraints, nextField, Set.copyOf(nextAsked),
                        List.of(), depth + 1);
                return Resolution.next(continuation);
            }
            if (!completeShape(nextSubjects, nextOutputs, nextSize, nextConstraints)) {
                return Resolution.noInformation();
            }
            return Resolution.resolved(toProposal(
                    nextSubjects, nextOutputs, nextSize, nextConstraints));
        } catch (IllegalArgumentException incomplete) {
            return Resolution.noInformation();
        }
    }

    private UserGoalProposal toProposal(
            List<Subject> resolvedSubjects,
            Set<GoalRequestedOutput> resolvedOutputs,
            Integer resolvedSize,
            Set<String> resolvedConstraints) {
        if (resolvedOutputs.isEmpty()) {
            throw new IllegalArgumentException("requested output remains unresolved");
        }
        UserGoalProposal.GoalParameters parameters = switch (goalKind) {
            case PORTFOLIO_FACT -> new UserGoalProposal.PortfolioFactParameters(facets);
            case PORTFOLIO_COMPARE -> new UserGoalProposal.PortfolioCompareParameters(dimensions);
            case PORTFOLIO_RECOMMEND -> new UserGoalProposal.PortfolioRecommendationParameters(
                    Objects.requireNonNull(resolvedSize, "requestedSize"), resolvedConstraints);
            case PORTFOLIO_REFINE_RECOMMENDATION ->
                    throw new IllegalArgumentException("refinement clarification is unsupported");
            default -> throw new IllegalArgumentException("goal kind cannot be restored safely");
        };
        UserGoalProposal.InputAnchor safeAnchor = new UserGoalProposal.InputAnchor(
                RESTORED_STATEMENT, 0);
        List<GoalSubjectReference> references = resolvedSubjects.stream()
                .map(subject -> new GoalSubjectReference(
                        subject.kind(), subject.reference(),
                        GoalSubjectReference.Basis.CONTINUATION, null))
                .toList();
        UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                goalKey(goalKind), goalKind, safeAnchor, references, resolvedOutputs,
                knowledgeRequirement(goalKind), parameters);
        return new UserGoalProposal(List.of(goal));
    }

    private void validatePartialShape() {
        validateUnresolvedField();
        switch (goalKind) {
            case PORTFOLIO_FACT -> {
                if (facets.isEmpty()) throw new IllegalArgumentException("facets are required");
                if (isPending(ClarificationProposal.Field.SUBJECT)
                        ? !subjects.isEmpty() : subjects.size() != 1) {
                    throw new IllegalArgumentException("portfolio fact subject is required");
                }
            }
            case PORTFOLIO_COMPARE -> {
                if (dimensions.isEmpty()) throw new IllegalArgumentException("dimensions are required");
                boolean validSubjects = isPending(ClarificationProposal.Field.SUBJECT)
                        ? subjects.size() >= 1 && subjects.size() < 5
                        : subjects.size() >= 2 && subjects.size() <= 5;
                if (!validSubjects) {
                    throw new IllegalArgumentException("portfolio comparison subjects are required");
                }
            }
            case PORTFOLIO_RECOMMEND -> {
                if (!subjects.isEmpty()) {
                    throw new IllegalArgumentException("recommendation does not require named subjects");
                }
                if (requestedSize != null && (requestedSize < 1 || requestedSize > 5)) {
                    throw new IllegalArgumentException("requestedSize must be between one and five");
                }
                if (requestedSize == null
                        && !isPending(ClarificationProposal.Field.REQUESTED_SIZE)) {
                    throw new IllegalArgumentException("missing requestedSize must be unresolved");
                }
            }
            case PORTFOLIO_REFINE_RECOMMENDATION ->
                    throw new IllegalArgumentException("refinement clarification is unsupported");
            default -> throw new IllegalArgumentException("unsupported blocked goal kind");
        }
        if (requestedOutputs.isEmpty()
                && !isPending(ClarificationProposal.Field.OUTPUT)) {
            throw new IllegalArgumentException("requested output is required");
        }
        if (!requestedOutputs.isEmpty() && !outputsSupported(goalKind, requestedOutputs)) {
            throw new IllegalArgumentException("requested output does not match goal kind");
        }
    }

    private boolean isPending(ClarificationProposal.Field field) {
        return unresolvedField == field || remainingFields.contains(field);
    }

    private void validateUnresolvedField() {
        List<ClarificationProposal.Field> missingFields = new java.util.ArrayList<>();
        missingFields.add(unresolvedField);
        missingFields.addAll(remainingFields);
        for (ClarificationProposal.Field field : missingFields) {
            validateAllowedField(field);
            if (!fieldIsMissing(field)) {
                throw new IllegalArgumentException("unresolved field is already populated");
            }
        }
    }

    private void validateAllowedField(ClarificationProposal.Field field) {
        boolean allowed = switch (goalKind) {
            case PORTFOLIO_FACT, PORTFOLIO_COMPARE ->
                    field == ClarificationProposal.Field.SUBJECT
                            || field == ClarificationProposal.Field.OUTPUT;
            case PORTFOLIO_RECOMMEND ->
                    field == ClarificationProposal.Field.REQUESTED_SIZE;
            case PORTFOLIO_REFINE_RECOMMENDATION -> false;
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("unresolved field does not match goal kind");
    }

    private boolean fieldIsMissing(ClarificationProposal.Field field) {
        return switch (field) {
            case SUBJECT -> subjects.isEmpty()
                    || goalKind == GoalKind.PORTFOLIO_COMPARE && subjects.size() == 1;
            case OUTPUT -> requestedOutputs.isEmpty();
            case REQUESTED_SIZE -> requestedSize == null;
            case CONSTRAINT -> constraints.isEmpty();
            case GOAL -> false;
        };
    }

    private boolean outputsSupported(GoalKind kind, Set<GoalRequestedOutput> outputs) {
        return switch (kind) {
            case PORTFOLIO_FACT -> outputs.stream().allMatch(value -> switch (value) {
                case OVERVIEW, BACKGROUND, RESPONSIBILITY, SOLUTION, VERIFICATION, STATUS -> true;
                default -> false;
            });
            case PORTFOLIO_COMPARE -> outputs.equals(Set.of(GoalRequestedOutput.COMPARISON));
            case PORTFOLIO_RECOMMEND, PORTFOLIO_REFINE_RECOMMENDATION ->
                    outputs.equals(Set.of(GoalRequestedOutput.RECOMMENDATION));
            default -> false;
        };
    }

    private boolean completeShape(
            List<Subject> resolvedSubjects,
            Set<GoalRequestedOutput> resolvedOutputs,
            Integer resolvedSize,
            Set<String> resolvedConstraints) {
        if (resolvedOutputs.isEmpty() || !outputsSupported(goalKind, resolvedOutputs)) return false;
        return switch (goalKind) {
            case PORTFOLIO_FACT -> resolvedSubjects.size() == 1 && !facets.isEmpty();
            case PORTFOLIO_COMPARE -> resolvedSubjects.size() >= 2
                    && resolvedSubjects.size() <= 5 && !dimensions.isEmpty();
            case PORTFOLIO_RECOMMEND -> resolvedSubjects.isEmpty()
                    && resolvedSize != null && resolvedSize >= 1 && resolvedSize <= 5;
            case PORTFOLIO_REFINE_RECOMMENDATION -> resolvedSubjects.size() == 1
                    && !resolvedConstraints.isEmpty();
            default -> false;
        };
    }

    private List<Subject> mergeSubjects(List<Subject> existing, List<Subject> additions) {
        java.util.LinkedHashMap<String, Subject> merged = new java.util.LinkedHashMap<>();
        existing.forEach(value -> merged.put(value.kind().name() + ':' + value.reference(), value));
        additions.forEach(value -> merged.put(value.kind().name() + ':' + value.reference(), value));
        return List.copyOf(merged.values());
    }

    private static Set<String> closedNames(Set<String> values, String name, boolean allowEmpty) {
        Set<String> copied = Set.copyOf(values == null ? Set.of() : values);
        if (!allowEmpty && copied.isEmpty()) throw new IllegalArgumentException(name + " is required");
        if (copied.stream().anyMatch(value -> value == null
                || !value.matches("[A-Z_]{1,64}"))) {
            throw new IllegalArgumentException(name + " must contain closed names");
        }
        return copied;
    }

    public GoalKind getGoalKind() { return goalKind; }
    public List<Subject> getSubjects() { return subjects; }
    public Set<GoalRequestedOutput> getRequestedOutputs() { return requestedOutputs; }
    public Set<UserGoalProposal.Facet> getFacets() { return facets; }
    public Set<String> getDimensions() { return dimensions; }
    public Integer getRequestedSize() { return requestedSize; }
    public Set<String> getConstraints() { return constraints; }
    public ClarificationProposal.Field getUnresolvedField() { return unresolvedField; }
    public Set<ClarificationProposal.Field> getAskedFields() { return askedFields; }
    public List<ClarificationProposal.Field> getRemainingFields() { return remainingFields; }
    public int getDepth() { return depth; }

    private String goalKey(GoalKind kind) {
        return switch (kind) {
            case PORTFOLIO_FACT -> "clarified-portfolio-fact";
            case PORTFOLIO_COMPARE -> "clarified-portfolio-comparison";
            case PORTFOLIO_RECOMMEND -> "clarified-portfolio-recommendation";
            case PORTFOLIO_REFINE_RECOMMENDATION -> "clarified-portfolio-refinement";
            default -> throw new IllegalArgumentException("unsupported blocked goal kind");
        };
    }

    private GoalKnowledgeRequirement knowledgeRequirement(GoalKind kind) {
        return switch (kind) {
            case PORTFOLIO_FACT, PORTFOLIO_COMPARE, PORTFOLIO_RECOMMEND,
                    PORTFOLIO_REFINE_RECOMMENDATION ->
                    GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE;
            default -> throw new IllegalArgumentException("unsupported blocked goal kind");
        };
    }

    public record Subject(GoalSubjectReference.Kind kind, String reference) {
        @JsonCreator
        public Subject(
                @JsonProperty("kind") GoalSubjectReference.Kind kind,
                @JsonProperty("reference") String reference) {
            this.kind = Objects.requireNonNull(kind, "kind");
            if (reference == null || reference.isBlank() || reference.length() > 128) {
                throw new IllegalArgumentException("public subject reference is invalid");
            }
            this.reference = reference;
        }
    }

    public interface ResolutionValue {
        ClarificationProposal.Field field();
    }
    public record SubjectValue(List<Subject> subjects) implements ResolutionValue {
        public SubjectValue { subjects = List.copyOf(subjects); }
        @Override public ClarificationProposal.Field field() {
            return ClarificationProposal.Field.SUBJECT;
        }
    }
    public record RequestedSizeValue(int requestedSize) implements ResolutionValue {
        public RequestedSizeValue {
            if (requestedSize < 1 || requestedSize > 5) {
                throw new IllegalArgumentException("requestedSize must be between one and five");
            }
        }
        @Override public ClarificationProposal.Field field() {
            return ClarificationProposal.Field.REQUESTED_SIZE;
        }
    }
    public record OutputValue(Set<GoalRequestedOutput> outputs) implements ResolutionValue {
        public OutputValue { outputs = Set.copyOf(outputs); }
        @Override public ClarificationProposal.Field field() {
            return ClarificationProposal.Field.OUTPUT;
        }
    }
    public record ConstraintValue(Set<String> constraints) implements ResolutionValue {
        public ConstraintValue { constraints = closedNames(constraints, "constraints", false); }
        @Override public ClarificationProposal.Field field() {
            return ClarificationProposal.Field.CONSTRAINT;
        }
    }

    public record Resolution(
            Kind kind, UserGoalProposal proposal, BlockedGoalTemplate continuation) {
        public Resolution { Objects.requireNonNull(kind, "kind"); }
        public static Resolution resolved(UserGoalProposal proposal) {
            return new Resolution(
                    Kind.RESOLVED, Objects.requireNonNull(proposal, "proposal"), null);
        }
        public static Resolution next(BlockedGoalTemplate continuation) {
            return new Resolution(
                    Kind.NEXT_CLARIFICATION, null,
                    Objects.requireNonNull(continuation, "continuation"));
        }
        public static Resolution noInformation() {
            return new Resolution(Kind.NO_INFORMATION, null, null);
        }
        public enum Kind { RESOLVED, NEXT_CLARIFICATION, NO_INFORMATION }
    }
}
