package com.portfolio.agent.answer.routing.adapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import com.portfolio.agent.answer.routing.domain.TextAnchor;
import com.portfolio.agent.answer.routing.domain.TurnProposal;
import com.portfolio.agent.answer.routing.gateway.TurnInterpretationPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Strictly decodes a provider payload into an untrusted, non-executable turn proposal. */
public final class TurnProposalCodec {

    private final ObjectMapper objectMapper;

    public TurnProposalCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.objectMapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public TurnInterpretationPort.TurnInterpretationResult decode(
            String payload,
            TurnInterpretationPort.TurnInterpretationInput input) {
        Objects.requireNonNull(input, "input");
        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("payload is required");
            }
            return TurnInterpretationPort.TurnInterpretationResult.success(
                    decode(objectMapper.readValue(payload, WireProposal.class), input));
        } catch (Exception exception) {
            return TurnInterpretationPort.TurnInterpretationResult.failure(
                    ConversationModelFailureCode.INVALID_RESPONSE);
        }
    }

    private TurnProposal decode(WireProposal proposal, TurnInterpretationPort.TurnInterpretationInput input) {
        if (proposal == null || proposal.getKind() == null) {
            throw new IllegalArgumentException("proposal kind is required");
        }
        TurnProposal.Kind kind = parseEnum(TurnProposal.Kind.class, proposal.getKind(), "kind");
        return switch (kind) {
            case PROPOSE_EXECUTION -> execution(proposal, input);
            case ASK_CLARIFICATION -> clarification(proposal, input);
            case CONVERSE -> conversation(proposal);
        };
    }

    private TurnProposal execution(WireProposal proposal, TurnInterpretationPort.TurnInterpretationInput input) {
        require(proposal.isTasksPresent() && !proposal.isClarificationPresent()
                && !proposal.isConversationActPresent() && !proposal.isSuggestedActionIdsPresent(),
                "execution fields are incompatible");
        List<WireTask> wireTasks = Objects.requireNonNull(proposal.getTasks(), "tasks");
        List<TurnProposal.TaskProposal> tasks = new ArrayList<>();
        for (WireTask wireTask : wireTasks) {
            tasks.add(task(wireTask, input));
        }
        List<TurnProposal.ProposalDependency> dependencies = proposal.isDependenciesPresent()
                ? dependencies(proposal.getDependencies()) : List.of();
        return TurnProposal.execution(tasks, dependencies);
    }

    private TurnProposal clarification(WireProposal proposal, TurnInterpretationPort.TurnInterpretationInput input) {
        require(!proposal.isTasksPresent() && proposal.isClarificationPresent()
                && !proposal.isConversationActPresent() && !proposal.isSuggestedActionIdsPresent(),
                "clarification fields are incompatible");
        WireClarification clarification = Objects.requireNonNull(proposal.getClarification(), "clarification");
        return TurnProposal.clarification(new TurnProposal.Clarification(
                clarification.getField(), anchor(clarification.getInputAnchor(), input)));
    }

    private TurnProposal conversation(WireProposal proposal) {
        require(!proposal.isTasksPresent() && !proposal.isClarificationPresent()
                && proposal.isConversationActPresent(), "conversation fields are incompatible");
        TurnProposal.ConversationAct conversationAct = parseEnum(
                TurnProposal.ConversationAct.class, proposal.getConversationAct(), "conversationAct");
        List<String> actionIds = proposal.isSuggestedActionIdsPresent()
                ? Objects.requireNonNull(proposal.getSuggestedActionIds(), "suggestedActionIds") : List.of();
        return TurnProposal.converse(conversationAct, actionIds);
    }

    private TurnProposal.TaskProposal task(
            WireTask wireTask,
            TurnInterpretationPort.TurnInterpretationInput input) {
        if (wireTask == null || wireTask.getClientTaskKey() == null || wireTask.getTaskType() == null
                || wireTask.getInputAnchor() == null || wireTask.getSubjectCandidates() == null
                || wireTask.getRequestedOutputs() == null) {
            throw new IllegalArgumentException("task fields are required");
        }
        SemanticTaskType taskType = parseEnum(SemanticTaskType.class, wireTask.getTaskType(), "taskType");
        if (!input.getAllowedTaskTypes().contains(taskType)) {
            throw new IllegalArgumentException("taskType is not allowed");
        }
        List<TurnProposal.SubjectCandidate> subjectCandidates = subjectCandidates(
                wireTask.getSubjectCandidates(), input);
        return new TurnProposal.TaskProposal(wireTask.getClientTaskKey(), taskType,
                anchor(wireTask.getInputAnchor(), input), subjectCandidates,
                parseEnums(RequestedOutput.class, wireTask.getRequestedOutputs(), "requestedOutputs"),
                anchors(wireTask.getTopicAnchors(), input), wireTask.getSourceTaskKeys() == null
                        ? List.of() : List.copyOf(wireTask.getSourceTaskKeys()), wireTask.getResponseMode() == null
                        ? TurnProposal.ResponseMode.STANDARD
                        : parseEnum(TurnProposal.ResponseMode.class, wireTask.getResponseMode(), "responseMode"),
                parseStringSet(wireTask.getFacets(), "facets"),
                parseStringSet(wireTask.getDimensions(), "dimensions"), wireTask.getCareerTrack(),
                parseStringSet(wireTask.getCapabilityFilters(), "capabilityFilters"), wireTask.getRequestedSize(),
                parseStringSet(wireTask.getConstraints(), "constraints"));
    }

    private List<TextAnchor> anchors(
            List<WireAnchor> anchors, TurnInterpretationPort.TurnInterpretationInput input) {
        if (anchors == null) return List.of();
        return anchors.stream().map(value -> anchor(value, input)).toList();
    }

    private TextAnchor anchor(WireAnchor wireAnchor, TurnInterpretationPort.TurnInterpretationInput input) {
        if (wireAnchor == null || wireAnchor.getOccurrence() == null) {
            throw new IllegalArgumentException("inputAnchor is required");
        }
        TextAnchor anchor = new TextAnchor(wireAnchor.getVerbatimText(), wireAnchor.getOccurrence());
        anchor.resolveIn(input.getCurrentInput());
        return anchor;
    }

    private List<TurnProposal.SubjectCandidate> subjectCandidates(
            List<WireSubject> candidates, TurnInterpretationPort.TurnInterpretationInput input) {
        List<TurnProposal.SubjectCandidate> resolved = new ArrayList<>();
        for (WireSubject candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("subject is required");
            }
            SubjectType type = parseEnum(SubjectType.class, candidate.getSubjectType(), "subjectType");
            TurnProposal.SubjectBasis basis = parseEnum(
                    TurnProposal.SubjectBasis.class, candidate.getBasis(), "basis");
            TextAnchor evidenceAnchor = candidate.getEvidenceAnchor() == null
                    ? null : anchor(candidate.getEvidenceAnchor(), input);
            resolved.add(new TurnProposal.SubjectCandidate(type, candidate.getSubjectId(), basis,
                    evidenceAnchor, candidate.getResultSetId(), candidate.getResultPosition()));
        }
        return List.copyOf(resolved);
    }

    private List<TurnProposal.ProposalDependency> dependencies(List<WireDependency> dependencies) {
        List<TurnProposal.ProposalDependency> parsed = new ArrayList<>();
        for (WireDependency dependency : Objects.requireNonNull(dependencies, "dependencies")) {
            if (dependency == null) {
                throw new IllegalArgumentException("dependency is required");
            }
            parsed.add(new TurnProposal.ProposalDependency(dependency.getFromClientTaskKey(),
                    dependency.getToClientTaskKey(), parseEnum(TaskDependencyType.class,
                            dependency.getDependencyType(), "dependencyType")));
        }
        return List.copyOf(parsed);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " is not supported", exception);
        }
    }

    private <E extends Enum<E>> Set<E> parseEnums(Class<E> type, List<String> values, String name) {
        Set<E> parsed = new LinkedHashSet<>();
        for (String value : values) {
            parsed.add(parseEnum(type, value, name));
        }
        return Set.copyOf(parsed);
    }

    private Set<String> parseStringSet(List<String> values, String name) {
        if (values == null) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !value.matches("[A-Z_]{1,64}")) {
                throw new IllegalArgumentException(name + " contains an invalid closed value");
            }
            parsed.add(value);
        }
        if (parsed.size() != values.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return Set.copyOf(parsed);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireProposal {
        private String kind;
        private List<WireTask> tasks;
        private List<WireDependency> dependencies;
        private WireClarification clarification;
        private String conversationAct;
        private List<String> suggestedActionIds;
        private boolean tasksPresent;
        private boolean dependenciesPresent;
        private boolean clarificationPresent;
        private boolean conversationActPresent;
        private boolean suggestedActionIdsPresent;

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public List<WireTask> getTasks() { return tasks; }
        public void setTasks(List<WireTask> tasks) { this.tasksPresent = true; this.tasks = tasks; }
        public List<WireDependency> getDependencies() { return dependencies; }
        public void setDependencies(List<WireDependency> dependencies) {
            this.dependenciesPresent = true; this.dependencies = dependencies;
        }
        public WireClarification getClarification() { return clarification; }
        public void setClarification(WireClarification clarification) {
            this.clarificationPresent = true; this.clarification = clarification;
        }
        public String getConversationAct() { return conversationAct; }
        public void setConversationAct(String conversationAct) {
            this.conversationActPresent = true; this.conversationAct = conversationAct;
        }
        public List<String> getSuggestedActionIds() { return suggestedActionIds; }
        public void setSuggestedActionIds(List<String> suggestedActionIds) {
            this.suggestedActionIdsPresent = true; this.suggestedActionIds = suggestedActionIds;
        }
        public boolean isTasksPresent() { return tasksPresent; }
        public boolean isDependenciesPresent() { return dependenciesPresent; }
        public boolean isClarificationPresent() { return clarificationPresent; }
        public boolean isConversationActPresent() { return conversationActPresent; }
        public boolean isSuggestedActionIdsPresent() { return suggestedActionIdsPresent; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireTask {
        private String clientTaskKey;
        private String taskType;
        private WireAnchor inputAnchor;
        private List<WireSubject> subjectCandidates;
        private List<WireAnchor> topicAnchors;
        private List<String> sourceTaskKeys;
        private List<String> requestedOutputs;
        private String responseMode;
        private List<String> facets;
        private List<String> dimensions;
        private String careerTrack;
        private List<String> capabilityFilters;
        private Integer requestedSize;
        private List<String> constraints;

        public String getClientTaskKey() { return clientTaskKey; }
        public void setClientTaskKey(String clientTaskKey) { this.clientTaskKey = clientTaskKey; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public WireAnchor getInputAnchor() { return inputAnchor; }
        public void setInputAnchor(WireAnchor inputAnchor) { this.inputAnchor = inputAnchor; }
        public List<WireSubject> getSubjectCandidates() { return subjectCandidates; }
        public void setSubjectCandidates(List<WireSubject> subjectCandidates) {
            this.subjectCandidates = subjectCandidates;
        }
        public List<WireAnchor> getTopicAnchors() { return topicAnchors; }
        public void setTopicAnchors(List<WireAnchor> topicAnchors) { this.topicAnchors = topicAnchors; }
        public List<String> getSourceTaskKeys() { return sourceTaskKeys; }
        public void setSourceTaskKeys(List<String> sourceTaskKeys) { this.sourceTaskKeys = sourceTaskKeys; }
        public List<String> getRequestedOutputs() { return requestedOutputs; }
        public void setRequestedOutputs(List<String> requestedOutputs) { this.requestedOutputs = requestedOutputs; }
        public String getResponseMode() { return responseMode; }
        public void setResponseMode(String responseMode) { this.responseMode = responseMode; }
        public List<String> getFacets() { return facets; }
        public void setFacets(List<String> facets) { this.facets = facets; }
        public List<String> getDimensions() { return dimensions; }
        public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }
        public String getCareerTrack() { return careerTrack; }
        public void setCareerTrack(String careerTrack) { this.careerTrack = careerTrack; }
        public List<String> getCapabilityFilters() { return capabilityFilters; }
        public void setCapabilityFilters(List<String> capabilityFilters) { this.capabilityFilters = capabilityFilters; }
        public Integer getRequestedSize() { return requestedSize; }
        public void setRequestedSize(Integer requestedSize) { this.requestedSize = requestedSize; }
        public List<String> getConstraints() { return constraints; }
        public void setConstraints(List<String> constraints) { this.constraints = constraints; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireDependency {
        private String fromClientTaskKey;
        private String toClientTaskKey;
        private String dependencyType;

        public String getFromClientTaskKey() { return fromClientTaskKey; }
        public void setFromClientTaskKey(String fromClientTaskKey) { this.fromClientTaskKey = fromClientTaskKey; }
        public String getToClientTaskKey() { return toClientTaskKey; }
        public void setToClientTaskKey(String toClientTaskKey) { this.toClientTaskKey = toClientTaskKey; }
        public String getDependencyType() { return dependencyType; }
        public void setDependencyType(String dependencyType) { this.dependencyType = dependencyType; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireAnchor {
        private String verbatimText;
        private Integer occurrence;

        public String getVerbatimText() { return verbatimText; }
        public void setVerbatimText(String verbatimText) { this.verbatimText = verbatimText; }
        public Integer getOccurrence() { return occurrence; }
        public void setOccurrence(Integer occurrence) { this.occurrence = occurrence; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireSubject {
        private String subjectType;
        private String subjectId;
        private String basis;
        private WireAnchor evidenceAnchor;
        private String resultSetId;
        private Integer resultPosition;

        public String getSubjectType() { return subjectType; }
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public String getBasis() { return basis; }
        public void setBasis(String basis) { this.basis = basis; }
        public WireAnchor getEvidenceAnchor() { return evidenceAnchor; }
        public void setEvidenceAnchor(WireAnchor evidenceAnchor) { this.evidenceAnchor = evidenceAnchor; }
        public String getResultSetId() { return resultSetId; }
        public void setResultSetId(String resultSetId) { this.resultSetId = resultSetId; }
        public Integer getResultPosition() { return resultPosition; }
        public void setResultPosition(Integer resultPosition) { this.resultPosition = resultPosition; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    static final class WireClarification {
        private String field;
        private WireAnchor inputAnchor;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public WireAnchor getInputAnchor() { return inputAnchor; }
        public void setInputAnchor(WireAnchor inputAnchor) { this.inputAnchor = inputAnchor; }
    }
}
