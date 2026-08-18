package com.portfolio.agent.evaluation.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import com.portfolio.agent.evaluation.grading.EvalGrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical JSON fact source for an eval run. Serialization is deterministic:
 * maps are ordered, lists are sorted by stable keys, and only whitelisted
 * sanitized fields are emitted.
 */
public final class EvalReportJsonWriter {

    private final ObjectMapper mapper;

    public EvalReportJsonWriter() {
        this.mapper = new ObjectMapper();
    }

    public String write(EvalRunReport report) {
        return write(report, java.util.List.of());
    }

    /**
     * Writes the report plus the expanded case manifest (handwritten cases
     * merged with runtime-generated smoke cases) so consumers can verify
     * exactly which cases were executed and how they were derived.
     */
    public String write(EvalRunReport report,
                        java.util.List<com.portfolio.agent.evaluation.domain.EvalCase> expandedCases) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("runId", report.getRunId());
        document.put("mode", report.getMode().name());
        document.put("verdict", report.getVerdict().name());
        document.put("identity", identity(report.getIdentity()));

        Map<String, Object> metrics = new LinkedHashMap<>();
        List<String> metricNames = new ArrayList<>(report.getMetrics().getAll().keySet());
        metricNames.sort(String::compareTo);
        for (String name : metricNames) {
            EvalMetrics.MetricValue value = report.getMetrics().getAll().get(name);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", value.getValue());
            entry.put("numerator", value.getNumerator());
            entry.put("denominator", value.getDenominator());
            metrics.put(name, entry);
        }
        document.put("metrics", metrics);

        List<Map<String, Object>> gates = new ArrayList<>();
        List<EvalGateResult> sortedGates = new ArrayList<>(report.getGates());
        sortedGates.sort(Comparator.comparing(EvalGateResult::getMetricName));
        for (EvalGateResult gate : sortedGates) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("metricName", gate.getMetricName());
            entry.put("observed", gate.getObserved());
            entry.put("threshold", gate.getThreshold());
            entry.put("comparison", gate.getComparison().name());
            entry.put("passed", gate.isPassed());
            entry.put("severity", gate.getSeverity().name());
            entry.put("reasonCode", gate.getReasonCode().name());
            gates.add(entry);
        }
        document.put("gates", gates);

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("comparable", report.getComparison().isComparable());
        comparison.put("reasonCode", report.getComparison().getReasonCode());
        comparison.put("deltas", report.getComparison().getDeltas());
        comparison.put("addedCaseIds", report.getComparison().getAddedCaseIds());
        comparison.put("removedCaseIds", report.getComparison().getRemovedCaseIds());
        document.put("comparison", comparison);

        List<Map<String, Object>> observations = new ArrayList<>();
        List<EvalObservation> sortedObservations =
                new ArrayList<>(report.getObservations());
        sortedObservations.sort(Comparator
                .comparing(EvalObservation::getCaseId)
                .thenComparing(EvalObservation::getLayer)
                .thenComparingInt(EvalObservation::getTrialIndex));
        for (EvalObservation observation : sortedObservations) {
            observations.add(observationEntry(observation));
        }
        document.put("observations", observations);

        List<Map<String, Object>> grades = new ArrayList<>();
        List<EvalGrade> sortedGrades = new ArrayList<>(report.getGrades());
        sortedGrades.sort(Comparator
                .comparing(EvalGrade::getCaseId)
                .thenComparing(EvalGrade::getLayer)
                .thenComparing(EvalGrade::getGraderType)
                .thenComparingInt(EvalGrade::getTrialIndex));
        for (EvalGrade grade : sortedGrades) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("caseId", grade.getCaseId());
            entry.put("layer", grade.getLayer().name());
            entry.put("trialIndex", grade.getTrialIndex());
            entry.put("graderType", grade.getGraderType());
            entry.put("severity", grade.getSeverity().name());
            entry.put("passed", grade.isPassed());
            entry.put("reasonCode", grade.getReasonCode().name());
            entry.put("numerator", grade.getNumerator());
            entry.put("denominator", grade.getDenominator());
            grades.add(entry);
        }
        document.put("grades", grades);

        document.put("baselineId", report.getBaselineId().orElse(null));
        boolean realProviderRan = report.getProviderAuthorization()
                == com.portfolio.agent.evaluation.domain
                        .EvalProviderAuthorization.REAL_AUTHORIZED
                && report.getObservations().stream()
                        .anyMatch(EvalObservation::isProviderInvoked);
        document.put("providerRealState", realProviderRan ? "REAL" : "INCOMPLETE");
        document.put("expandedCases", mapper.valueToTree(expandedCases));
        try {
            return mapper.writeValueAsString(document);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Failed to serialize eval report", failure);
        }
    }

    private Map<String, Object> identity(EvalRunIdentity identity) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("gitCommit", identity.getGitCommit());
        entry.put("datasetVersion", identity.getDatasetVersion());
        entry.put("datasetHash", identity.getDatasetHash());
        entry.put("bundleVersion", identity.getBundleVersion());
        entry.put("bundleHash", identity.getBundleHash());
        entry.put("promptHash", identity.getPromptHash());
        entry.put("retrievalPolicyHash", identity.getRetrievalPolicyHash());
        entry.put("embeddingModel", identity.getEmbeddingModel());
        entry.put("embeddingArtifactHash", identity.getEmbeddingArtifactHash());
        entry.put("provider", identity.getProvider());
        entry.put("model", identity.getModel());
        entry.put("modelParametersHash", identity.getModelParametersHash());
        entry.put("judgeModel", identity.getJudgeModel());
        entry.put("judgeRubricVersion", identity.getJudgeRubricVersion());
        return entry;
    }

    private Map<String, Object> observationEntry(EvalObservation observation) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("caseId", observation.getCaseId());
        entry.put("layer", observation.getLayer().name());
        entry.put("trialIndex", observation.getTrialIndex());
        entry.put("status", observation.getStatus().name());
        entry.put("reasonCodes", observation.getReasonCodes());
        entry.put("durationMilliseconds", observation.getDurationMilliseconds());
        entry.put("degraded", observation.isDegraded());
        entry.put("providerInvoked", observation.isProviderInvoked());
        Map<String, Object> shape = new LinkedHashMap<>();
        shape.put("blockCount", observation.getAnswerShape().getBlockCount());
        shape.put("characterCount", observation.getAnswerShape().getCharacterCount());
        shape.put("distinctClaimCount", observation.getAnswerShape().getDistinctClaimCount());
        shape.put("distinctEvidenceCount", observation.getAnswerShape().getDistinctEvidenceCount());
        shape.put("repeatedClaimReferenceCount",
                observation.getAnswerShape().getRepeatedClaimReferenceCount());
        shape.put("repeatedEvidenceReferenceCount",
                observation.getAnswerShape().getRepeatedEvidenceReferenceCount());
        shape.put("repeatedContentCount",
                observation.getAnswerShape().getRepeatedContentCount());
        shape.put("repeatedSourceScopeCount",
                observation.getAnswerShape().getRepeatedSourceScopeCount());
        shape.put("semanticSectionCount",
                observation.getAnswerShape().getSemanticSectionCount());
        shape.put("typedSectionCount",
                observation.getAnswerShape().getTypedSectionCount());
        shape.put("untypedBlockCount",
                observation.getAnswerShape().getUntypedBlockCount());
        shape.put("sectionOrderValid",
                observation.getAnswerShape().isSectionOrderValid());
        shape.put("summaryPresent",
                observation.getAnswerShape().isSummaryPresent());
        shape.put("directAnswerPresent",
                observation.getAnswerShape().isDirectAnswerPresent());
        entry.put("answerShape", shape);
        entry.put("semanticTurnShape", semanticTurnShape(observation));
        return entry;
    }

    private Map<String, Object> semanticTurnShape(EvalObservation observation) {
        com.portfolio.agent.evaluation.domain.EvalSemanticTurnShape shape =
                observation.getSemanticTurnShape();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("disposition", shape.getDisposition().name());
        entry.put("planOutcome", shape.getCoverageOutcome().name());
        entry.put("taskCount", shape.getTaskCount());
        entry.put("dependencyCount", shape.getDependencyCount());
        entry.put("modelCallCount", shape.getModelCallCount());
        entry.put("answeredCount", shape.getAnsweredCount());
        entry.put("blockedCount", shape.getBlockedCount());
        entry.put("failedCount", shape.getFailedCount());
        entry.put("degradedCount", shape.getDegradedCount());
        entry.put("portfolioSourceTaskCount", shape.getPortfolioSourceTaskCount());
        entry.put("generalSourceTaskCount", shape.getGeneralSourceTaskCount());
        entry.put("synthesisSourceTaskCount", shape.getSynthesisSourceTaskCount());
        entry.put("planInvariantValid", shape.isPlanInvariantValid());
        entry.put("provenanceValid", shape.isProvenanceValid());
        entry.put("privacySafe", shape.isPrivacySafe());
        return entry;
    }
}
