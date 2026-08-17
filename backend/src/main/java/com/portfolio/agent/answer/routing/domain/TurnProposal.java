package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** A closed, untrusted interpretation proposal that has no execution authority. */
public final class TurnProposal {

    private static final int MAX_TASKS = 6;
    private static final int MAX_SUGGESTED_ACTIONS = 4;
    private static final Pattern LOCAL_KEY = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    private final Kind kind;
    private final List<TaskProposal> tasks;
    private final List<ProposalDependency> dependencies;
    private final Clarification clarification;
    private final ConversationAct conversationAct;
    private final List<String> suggestedActionIds;

    private TurnProposal(
            Kind kind,
            List<TaskProposal> tasks,
            List<ProposalDependency> dependencies,
            Clarification clarification,
            ConversationAct conversationAct,
            List<String> suggestedActionIds) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        this.dependencies = copyDistinctDependencies(dependencies, "dependencies");
        this.clarification = clarification;
        this.conversationAct = conversationAct;
        this.suggestedActionIds = List.copyOf(Objects.requireNonNull(suggestedActionIds, "suggestedActionIds"));
        validateExclusiveFields();
    }

    public static TurnProposal execution(List<TaskProposal> tasks) {
        return execution(tasks, List.of());
    }

    public static TurnProposal execution(List<TaskProposal> tasks, List<ProposalDependency> dependencies) {
        List<TaskProposal> copied = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        if (copied.isEmpty() || copied.size() > MAX_TASKS) {
            throw new IllegalArgumentException("execution proposal must contain one to six tasks");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (TaskProposal task : copied) {
            if (task == null || !keys.add(task.getClientTaskKey())) {
                throw new IllegalArgumentException("execution task keys must be distinct");
            }
        }
        List<ProposalDependency> copiedDependencies = copyDistinctDependencies(dependencies, "dependencies");
        for (ProposalDependency dependency : copiedDependencies) {
            if (!keys.contains(dependency.getFromClientTaskKey()) || !keys.contains(dependency.getToClientTaskKey())) {
                throw new IllegalArgumentException("proposal dependency must reference execution tasks");
            }
        }
        return new TurnProposal(Kind.PROPOSE_EXECUTION, copied, copiedDependencies, null, null, List.of());
    }

    public static TurnProposal clarification(Clarification clarification) {
        return new TurnProposal(Kind.ASK_CLARIFICATION, List.of(), List.of(),
                Objects.requireNonNull(clarification, "clarification"), null, List.of());
    }

    public static TurnProposal converse(ConversationAct conversationAct, List<String> suggestedActionIds) {
        List<String> actions = List.copyOf(Objects.requireNonNull(suggestedActionIds, "suggestedActionIds"));
        if (actions.size() > MAX_SUGGESTED_ACTIONS) {
            throw new IllegalArgumentException("conversation proposal has too many suggested actions");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String actionId : actions) {
            if (actionId == null || !LOCAL_KEY.matcher(actionId).matches() || !distinct.add(actionId)) {
                throw new IllegalArgumentException("suggestedActionIds must contain distinct local action ids");
            }
        }
        return new TurnProposal(Kind.CONVERSE, List.of(), List.of(), null,
                Objects.requireNonNull(conversationAct, "conversationAct"), actions);
    }

    public Kind getKind() { return kind; }
    public List<TaskProposal> getTasks() { return tasks; }
    public List<ProposalDependency> getDependencies() { return dependencies; }
    public Optional<Clarification> getClarification() { return Optional.ofNullable(clarification); }
    public Optional<ConversationAct> getConversationAct() { return Optional.ofNullable(conversationAct); }
    public List<String> getSuggestedActionIds() { return suggestedActionIds; }

    private void validateExclusiveFields() {
        boolean execution = kind == Kind.PROPOSE_EXECUTION;
        boolean clarificationKind = kind == Kind.ASK_CLARIFICATION;
        boolean conversation = kind == Kind.CONVERSE;
        if (execution != (!tasks.isEmpty() && clarification == null && conversationAct == null
                && suggestedActionIds.isEmpty())
                || (!execution && !dependencies.isEmpty())
                || clarificationKind != (tasks.isEmpty() && clarification != null && conversationAct == null
                && suggestedActionIds.isEmpty())
                || conversation != (tasks.isEmpty() && clarification == null && conversationAct != null)) {
            throw new IllegalArgumentException("proposal fields do not match its kind");
        }
    }

    public enum Kind { PROPOSE_EXECUTION, ASK_CLARIFICATION, CONVERSE }

    public enum ConversationAct { SOCIAL_ACKNOWLEDGEMENT, UNINTERPRETABLE, OUT_OF_SCOPE }

    public static final class TaskProposal {

        private final String clientTaskKey;
        private final SemanticTaskType taskType;
        private final TextAnchor inputAnchor;
        private final List<SubjectCandidate> subjectCandidates;
        private final Set<RequestedOutput> requestedOutputs;
        private final List<TextAnchor> topicAnchors;
        private final List<String> sourceTaskKeys;
        private final ResponseMode responseMode;
        private final Set<String> facets;
        private final Set<String> dimensions;
        private final String careerTrack;
        private final Set<String> capabilityFilters;
        private final Integer requestedSize;
        private final Set<String> constraints;

        public TaskProposal(
                String clientTaskKey,
                SemanticTaskType taskType,
                TextAnchor inputAnchor,
                List<SubjectCandidate> subjectCandidates,
                Set<RequestedOutput> requestedOutputs) {
            this(clientTaskKey, taskType, inputAnchor, subjectCandidates, requestedOutputs, List.of(), List.of(),
                    ResponseMode.STANDARD);
        }

        public TaskProposal(
                String clientTaskKey,
                SemanticTaskType taskType,
                TextAnchor inputAnchor,
                List<SubjectCandidate> subjectCandidates,
                Set<RequestedOutput> requestedOutputs,
                List<TextAnchor> topicAnchors,
                List<String> sourceTaskKeys) {
            this(clientTaskKey, taskType, inputAnchor, subjectCandidates, requestedOutputs, topicAnchors,
                    sourceTaskKeys, ResponseMode.STANDARD);
        }

        public TaskProposal(
                String clientTaskKey,
                SemanticTaskType taskType,
                TextAnchor inputAnchor,
                List<SubjectCandidate> subjectCandidates,
                Set<RequestedOutput> requestedOutputs,
                List<TextAnchor> topicAnchors,
                List<String> sourceTaskKeys,
                ResponseMode responseMode) {
            this(clientTaskKey, taskType, inputAnchor, subjectCandidates, requestedOutputs, topicAnchors,
                    sourceTaskKeys, responseMode, Set.of(), Set.of());
        }

        public TaskProposal(
                String clientTaskKey, SemanticTaskType taskType, TextAnchor inputAnchor,
                List<SubjectCandidate> subjectCandidates, Set<RequestedOutput> requestedOutputs,
                List<TextAnchor> topicAnchors, List<String> sourceTaskKeys, ResponseMode responseMode,
                Set<String> facets, Set<String> dimensions) {
            this(clientTaskKey, taskType, inputAnchor, subjectCandidates, requestedOutputs, topicAnchors,
                    sourceTaskKeys, responseMode, facets, dimensions, null, Set.of(), null);
        }

        public TaskProposal(
                String clientTaskKey, SemanticTaskType taskType, TextAnchor inputAnchor,
                List<SubjectCandidate> subjectCandidates, Set<RequestedOutput> requestedOutputs,
                List<TextAnchor> topicAnchors, List<String> sourceTaskKeys, ResponseMode responseMode,
                Set<String> facets, Set<String> dimensions, String careerTrack,
                Set<String> capabilityFilters, Integer requestedSize) {
            this(clientTaskKey, taskType, inputAnchor, subjectCandidates, requestedOutputs, topicAnchors,
                    sourceTaskKeys, responseMode, facets, dimensions, careerTrack, capabilityFilters,
                    requestedSize, Set.of());
        }

        public TaskProposal(
                String clientTaskKey, SemanticTaskType taskType, TextAnchor inputAnchor,
                List<SubjectCandidate> subjectCandidates, Set<RequestedOutput> requestedOutputs,
                List<TextAnchor> topicAnchors, List<String> sourceTaskKeys, ResponseMode responseMode,
                Set<String> facets, Set<String> dimensions, String careerTrack,
                Set<String> capabilityFilters, Integer requestedSize, Set<String> constraints) {
            if (clientTaskKey == null || !LOCAL_KEY.matcher(clientTaskKey).matches()) {
                throw new IllegalArgumentException("clientTaskKey must be a local key");
            }
            this.clientTaskKey = clientTaskKey;
            this.taskType = Objects.requireNonNull(taskType, "taskType");
            this.inputAnchor = Objects.requireNonNull(inputAnchor, "inputAnchor");
            this.subjectCandidates = copyDistinctCandidates(subjectCandidates, "subjectCandidates");
            this.requestedOutputs = Set.copyOf(Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
            this.topicAnchors = List.copyOf(Objects.requireNonNull(topicAnchors, "topicAnchors"));
            this.sourceTaskKeys = copyLocalKeys(sourceTaskKeys, "sourceTaskKeys");
            this.responseMode = Objects.requireNonNull(responseMode, "responseMode");
            this.facets = copyEnumNames(facets, "facets");
            this.dimensions = copyEnumNames(dimensions, "dimensions");
            this.careerTrack = careerTrack == null ? null : requireEnumName(careerTrack, "careerTrack");
            this.capabilityFilters = copyEnumNames(capabilityFilters, "capabilityFilters");
            if (requestedSize != null && (requestedSize < 1 || requestedSize > 5)) {
                throw new IllegalArgumentException("requestedSize must be between one and five");
            }
            this.requestedSize = requestedSize;
            this.constraints = copyEnumNames(constraints, "constraints");
        }

        public String getClientTaskKey() { return clientTaskKey; }
        public SemanticTaskType getTaskType() { return taskType; }
        public TextAnchor getInputAnchor() { return inputAnchor; }
        public List<SubjectCandidate> getSubjectCandidates() { return subjectCandidates; }
        public Set<RequestedOutput> getRequestedOutputs() { return requestedOutputs; }
        public List<TextAnchor> getTopicAnchors() { return topicAnchors; }
        public List<String> getSourceTaskKeys() { return sourceTaskKeys; }
        public ResponseMode getResponseMode() { return responseMode; }
        public Set<String> getFacets() { return facets; }
        public Set<String> getDimensions() { return dimensions; }
        public Optional<String> getCareerTrack() { return Optional.ofNullable(careerTrack); }
        public Set<String> getCapabilityFilters() { return capabilityFilters; }
        public Optional<Integer> getRequestedSize() { return Optional.ofNullable(requestedSize); }
        public Set<String> getConstraints() { return constraints; }
    }

    public enum ResponseMode { CONCISE, STANDARD, DETAILED }

    public static final class Clarification {

        private final ClarificationField field;
        private final TextAnchor inputAnchor;

        public Clarification(String field, TextAnchor inputAnchor) {
            this.field = parseField(field);
            this.inputAnchor = Objects.requireNonNull(inputAnchor, "inputAnchor");
        }

        public ClarificationField getField() { return field; }
        public TextAnchor getInputAnchor() { return inputAnchor; }

        private static ClarificationField parseField(String field) {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("clarification field is required");
            }
            try {
                return ClarificationField.valueOf(field);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("clarification field is not supported", exception);
            }
        }
    }

    public enum ClarificationField { SUBJECT, INTENT, OUTPUT }

    /** Closed, server-revalidated provenance for one proposed public subject. */
    public enum SubjectBasis {
        EXPLICIT_INPUT,
        PENDING_INTERACTION,
        CONFIRMED_SUBJECT,
        RECENT_RESULT,
        PAGE_HINT
    }

    public static final class SubjectCandidate {

        private final SubjectType subjectType;
        private final String subjectId;
        private final SubjectBasis basis;
        private final TextAnchor evidenceAnchor;
        private final String resultSetId;
        private final Integer resultPosition;

        public SubjectCandidate(
                SubjectType subjectType,
                String subjectId,
                SubjectBasis basis,
                TextAnchor evidenceAnchor,
                String resultSetId,
                Integer resultPosition) {
            this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
            if (subjectId == null || subjectId.isBlank() || subjectId.length() > 128) {
                throw new IllegalArgumentException("subjectId is required and bounded");
            }
            this.subjectId = subjectId.trim();
            this.basis = Objects.requireNonNull(basis, "basis");
            this.evidenceAnchor = evidenceAnchor;
            this.resultSetId = normalizeOptionalId(resultSetId, "resultSetId");
            this.resultPosition = resultPosition;
            validateBasisFields();
        }

        public SubjectType getSubjectType() { return subjectType; }
        public String getSubjectId() { return subjectId; }
        public SubjectBasis getBasis() { return basis; }
        public Optional<TextAnchor> getEvidenceAnchor() { return Optional.ofNullable(evidenceAnchor); }
        public Optional<String> getResultSetId() { return Optional.ofNullable(resultSetId); }
        public Optional<Integer> getResultPosition() { return Optional.ofNullable(resultPosition); }

        private void validateBasisFields() {
            boolean textEvidenceBasis = basis == SubjectBasis.EXPLICIT_INPUT || basis == SubjectBasis.PAGE_HINT;
            if (textEvidenceBasis != (evidenceAnchor != null)) {
                throw new IllegalArgumentException("text subject basis requires exactly one evidence anchor");
            }
            if (basis == SubjectBasis.RECENT_RESULT) {
                if (resultPosition == null || resultPosition < 1 || resultPosition > 5 || evidenceAnchor != null) {
                    throw new IllegalArgumentException("recent result basis requires a bounded result position only");
                }
                return;
            }
            if (resultSetId != null || resultPosition != null) {
                throw new IllegalArgumentException("only recent result basis may name a result position");
            }
        }
    }

    /** A local proposal edge; it never uses trusted task or plan identifiers. */
    public static final class ProposalDependency {

        private final String fromClientTaskKey;
        private final String toClientTaskKey;
        private final TaskDependencyType dependencyType;

        public ProposalDependency(
                String fromClientTaskKey,
                String toClientTaskKey,
                TaskDependencyType dependencyType) {
            this.fromClientTaskKey = requireLocalKey(fromClientTaskKey, "fromClientTaskKey");
            this.toClientTaskKey = requireLocalKey(toClientTaskKey, "toClientTaskKey");
            if (this.fromClientTaskKey.equals(this.toClientTaskKey)) {
                throw new IllegalArgumentException("proposal dependency cannot reference itself");
            }
            this.dependencyType = Objects.requireNonNull(dependencyType, "dependencyType");
        }

        public String getFromClientTaskKey() { return fromClientTaskKey; }
        public String getToClientTaskKey() { return toClientTaskKey; }
        public TaskDependencyType getDependencyType() { return dependencyType; }
    }

    private static List<SubjectReference> copyDistinct(List<SubjectReference> values, String name) {
        List<SubjectReference> copied = List.copyOf(Objects.requireNonNull(values, name));
        if (new LinkedHashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return copied;
    }

    private static List<SubjectCandidate> copyDistinctCandidates(
            List<SubjectCandidate> values, String name) {
        List<SubjectCandidate> copied = List.copyOf(Objects.requireNonNull(values, name));
        Set<String> keys = new LinkedHashSet<>();
        for (SubjectCandidate candidate : copied) {
            if (candidate == null || !keys.add(candidate.getSubjectType().name() + ":" + candidate.getSubjectId())) {
                throw new IllegalArgumentException(name + " must not contain duplicate subjects");
            }
        }
        return copied;
    }

    private static List<ProposalDependency> copyDistinctDependencies(
            List<ProposalDependency> values, String name) {
        List<ProposalDependency> copied = List.copyOf(Objects.requireNonNull(values, name));
        Set<String> keys = new LinkedHashSet<>();
        for (ProposalDependency dependency : copied) {
            if (dependency == null || !keys.add(dependency.getFromClientTaskKey() + "\u0000"
                    + dependency.getToClientTaskKey() + "\u0000" + dependency.getDependencyType().name())) {
                throw new IllegalArgumentException(name + " must not contain duplicate dependencies");
            }
        }
        return copied;
    }

    private static String normalizeOptionalId(String value, String name) {
        if (value == null) {
            return null;
        }
        if (!LOCAL_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a local key");
        }
        return value;
    }

    private static String requireLocalKey(String value, String name) {
        if (value == null || !LOCAL_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a local key");
        }
        return value;
    }

    private static List<String> copyLocalKeys(List<String> values, String name) {
        List<String> copied = List.copyOf(Objects.requireNonNull(values, name));
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : copied) {
            if (!distinct.add(requireLocalKey(value, name))) {
                throw new IllegalArgumentException(name + " must not contain duplicates");
            }
        }
        return copied;
    }

    private static Set<String> copyEnumNames(Set<String> values, String name) {
        Set<String> copied = Set.copyOf(Objects.requireNonNull(values, name));
        for (String value : copied) {
            if (value == null || !value.matches("[A-Z_]{1,64}")) {
                throw new IllegalArgumentException(name + " must contain closed enum names");
            }
        }
        return copied;
    }

    private static String requireEnumName(String value, String name) {
        if (value == null || !value.matches("[A-Z_]{1,64}")) {
            throw new IllegalArgumentException(name + " must be a closed enum name");
        }
        return value;
    }
}
