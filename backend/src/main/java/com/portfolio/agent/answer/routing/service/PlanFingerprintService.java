package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskDependency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Computes stable SHA-256 fingerprints for the semantic content of a plan. */
public final class PlanFingerprintService {

    public String fingerprint(SemanticTurnPlan plan, String contract) {
        Objects.requireNonNull(plan, "plan");
        String canonical = canonicalize(plan, requireText(contract, "contract"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + toLowerHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String canonicalize(SemanticTurnPlan plan, String contract) {
        StringBuilder builder = new StringBuilder();
        append(builder, "contract", contract);
        append(builder, "contentVersion", plan.getContentVersion());
        append(builder, "source", plan.getSource().name());
        appendTasks(builder, plan.getTasks());
        appendDependencies(builder, plan.getDependencies());
        appendExclusions(builder, plan.getExclusions());
        appendEnumCollection(builder, "requestedOutputs", plan.getRequestedOutputs());
        appendConfirmationPolicy(builder, plan.getConfirmationPolicy());
        return builder.toString();
    }

    private void appendTasks(StringBuilder builder, List<SemanticTask> tasks) {
        append(builder, "taskCount", Integer.toString(tasks.size()));
        for (int index = 0; index < tasks.size(); index++) {
            SemanticTask task = tasks.get(index);
            append(builder, "taskIndex", Integer.toString(index));
            append(builder, "taskId", task.getTaskId());
            append(builder, "taskType", task.getTaskType().name());
            append(builder, "taskSource", task.getSourceDomain().name());
            append(builder, "goalLabel", task.getGoalLabel());
            append(builder, "fulfillmentRole", task.getFulfillmentRole().name());
            appendParameters(builder, task.getParameters());
            appendEnumCollection(builder, "taskRequestedOutputs", task.getRequestedOutputs());
            appendConfidence(builder, task.getConfidence());
            appendSubjectList(builder, "taskSubjects", task.getSubjectReferences());
        }
    }

    private void appendParameters(StringBuilder builder, SemanticTaskParameters parameters) {
        append(builder, "parameterType", parameters.getClass().getName());
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            appendSubject(builder, "factSubject", fact.getSubject());
            appendEnumCollection(builder, "factFacets", fact.getFacets());
            append(builder, "factAudience", fact.getAudienceRole().name());
            return;
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            appendSubjectList(builder, "compareSubjects", comparison.getSubjects());
            appendEnumCollection(builder, "compareDimensions", comparison.getDimensions());
            append(builder, "compareAudience", comparison.getAudienceRole().name());
            return;
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            appendSubjectList(builder, "recommendCandidates", recommendation.getCandidateSubjects());
            append(builder, "recommendCareerTrack", recommendation.getCareerTrack().name());
            appendEnumCollection(builder, "recommendCapabilities", recommendation.getCapabilityCodes());
            append(builder, "recommendGoal", recommendation.getGoal());
            append(builder, "recommendSize", recommendation.getRequestedSize().name());
            append(builder, "recommendAudience", recommendation.getAudienceRole().name());
            return;
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRefinement refinement) {
            appendSubject(builder, "refinementBaseResult", refinement.getBaseResultReference());
            appendEnumCollection(builder, "refinementConstraints", refinement.getAddedConstraints());
            appendSubjectCollection(builder, "refinementRemovedSubjects", refinement.getRemovedSubjects());
            return;
        }
        if (parameters instanceof SemanticTaskParameters.GeneralExplanation explanation) {
            append(builder, "generalTopic", explanation.getTopic());
            append(builder, "generalDepth", explanation.getDepth().name());
            append(builder, "generalAudience", explanation.getAudienceRole().name());
            return;
        }
        if (parameters instanceof SemanticTaskParameters.GeneralComparison comparison) {
            appendStringList(builder, "generalSubjects", comparison.getSubjects());
            appendEnumCollection(builder, "generalDimensions", comparison.getDimensions());
            append(builder, "generalDepth", comparison.getDepth().name());
            append(builder, "generalAudience", comparison.getAudienceRole().name());
            return;
        }
        if (parameters instanceof SemanticTaskParameters.Synthesis synthesis) {
            appendStringList(builder, "synthesisSources", synthesis.getSourceTaskIds());
            append(builder, "synthesisGoal", synthesis.getSynthesisGoal());
            appendEnumCollection(builder, "synthesisDimensions", synthesis.getDimensions());
            return;
        }
        throw new IllegalArgumentException("unsupported semantic task parameters");
    }

    private void appendConfidence(StringBuilder builder, TaskConfidence confidence) {
        append(builder, "confidenceOverall", confidence.getOverall().name());
        append(builder, "confidenceOrigin", confidence.getOrigin().name());
        List<SemanticRoutingTypes.ConfidenceField> fields = new ArrayList<>(confidence.getFieldLevels().keySet());
        fields.sort(Comparator.comparing(Enum::name));
        append(builder, "confidenceFieldCount", Integer.toString(fields.size()));
        for (SemanticRoutingTypes.ConfidenceField field : fields) {
            append(builder, "confidenceField", field.name());
            append(builder, "confidenceLevel", confidence.getFieldLevels().get(field).name());
        }
    }

    private void appendDependencies(StringBuilder builder, List<TaskDependency> dependencies) {
        List<String> canonicalDependencies = new ArrayList<>();
        for (TaskDependency dependency : dependencies) {
            StringBuilder entry = new StringBuilder();
            append(entry, "from", dependency.getFromTaskId());
            append(entry, "to", dependency.getToTaskId());
            append(entry, "type", dependency.getType().name());
            append(entry, "origin", dependency.getOrigin().name());
            canonicalDependencies.add(entry.toString());
        }
        canonicalDependencies.sort(String::compareTo);
        appendStringList(builder, "dependencies", canonicalDependencies);
    }

    private void appendExclusions(StringBuilder builder, List<PlanExclusion> exclusions) {
        List<String> canonicalExclusions = new ArrayList<>();
        for (PlanExclusion exclusion : exclusions) {
            StringBuilder entry = new StringBuilder();
            append(entry, "scope", exclusion.getScope().name());
            append(entry, "type", exclusion.getType().name());
            append(entry, "taskId", nullToEmpty(exclusion.getTaskId()));
            appendExclusionValue(entry, exclusion.getControlledValue());
            canonicalExclusions.add(entry.toString());
        }
        canonicalExclusions.sort(String::compareTo);
        appendStringList(builder, "exclusions", canonicalExclusions);
    }

    private void appendExclusionValue(StringBuilder builder, PlanExclusion.ExclusionValue value) {
        if (value instanceof PlanExclusion.SubjectValue subjectValue) {
            append(builder, "valueType", "SUBJECT");
            appendSubject(builder, "valueSubject", subjectValue.getSubject());
            return;
        }
        if (value instanceof PlanExclusion.OutputValue outputValue) {
            append(builder, "valueType", "OUTPUT");
            append(builder, "valueOutput", outputValue.getOutput().name());
            return;
        }
        if (value instanceof PlanExclusion.DimensionValue dimensionValue) {
            append(builder, "valueType", "DIMENSION");
            append(builder, "valueDimension", dimensionValue.getDimension().name());
            return;
        }
        if (value instanceof PlanExclusion.ConstraintValue constraintValue) {
            append(builder, "valueType", "CONSTRAINT");
            append(builder, "valueConstraint", constraintValue.getConstraint().name());
            return;
        }
        throw new IllegalArgumentException("unsupported plan exclusion value");
    }

    private void appendConfirmationPolicy(
            StringBuilder builder, SemanticTurnPlan.PlanConfirmationPolicy policy) {
        append(builder, "confirmationRequired", Boolean.toString(policy.isConfirmationRequired()));
        appendEnumCollection(builder, "confirmationTriggers", policy.getTriggerCodes());
    }

    private void appendSubjectList(StringBuilder builder, String name, List<SubjectReference> subjects) {
        append(builder, name + "Count", Integer.toString(subjects.size()));
        for (int index = 0; index < subjects.size(); index++) {
            append(builder, name + "Index", Integer.toString(index));
            appendSubject(builder, name, subjects.get(index));
        }
    }

    private void appendSubjectCollection(
            StringBuilder builder, String name, Collection<SubjectReference> subjects) {
        List<String> canonicalSubjects = new ArrayList<>();
        for (SubjectReference subject : subjects) {
            canonicalSubjects.add(canonicalSubject(subject));
        }
        canonicalSubjects.sort(String::compareTo);
        appendStringList(builder, name, canonicalSubjects);
    }

    private void appendSubject(StringBuilder builder, String name, SubjectReference subject) {
        append(builder, name + "Type", subject.getSubjectType().name());
        append(builder, name + "Id", subject.getSubjectId());
        append(builder, name + "ResolutionSource", subject.getResolutionSource().name());
        append(builder, name + "ContentVersion", nullToEmpty(subject.getContentVersion()));
    }

    private String canonicalSubject(SubjectReference subject) {
        StringBuilder builder = new StringBuilder();
        appendSubject(builder, "subject", subject);
        return builder.toString();
    }

    private void appendEnumCollection(
            StringBuilder builder, String name, Collection<? extends Enum<?>> values) {
        List<String> names = new ArrayList<>();
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        names.sort(String::compareTo);
        appendStringList(builder, name, names);
    }

    private void appendStringList(StringBuilder builder, String name, List<String> values) {
        append(builder, name + "Count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            append(builder, name + "Index", Integer.toString(index));
            append(builder, name, values.get(index));
        }
    }

    private void append(StringBuilder builder, String name, String value) {
        String normalizedName = normalize(name);
        String normalizedValue = normalize(value);
        builder.append(normalizedName.length()).append(':').append(normalizedName)
                .append('=').append(normalizedValue.length()).append(':').append(normalizedValue)
                .append('\n');
    }

    private static String toLowerHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            builder.append(Character.forDigit(value & 0x0f, 16));
        }
        return builder.toString();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(Objects.requireNonNull(value, "value"), Normalizer.Form.NFC);
    }
}
