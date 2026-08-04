package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EvalSuiteLoader {

    private final ObjectMapper mapper;
    private final boolean externalChallengeSource;

    public EvalSuiteLoader(ObjectMapper mapper) {
        this(mapper, false);
    }

    public EvalSuiteLoader(ObjectMapper mapper, boolean externalChallengeSource) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.externalChallengeSource = externalChallengeSource;
    }

    public EvalSuite load(byte[] source) {
        EvalSuite suite;
        try {
            suite = mapper.readValue(source, EvalSuite.class);
        } catch (IOException | RuntimeException exception) {
            throw invalid(exception);
        }

        try {
            validateSuite(suite);
            List<EvalCase> sortedCases = new ArrayList<EvalCase>(suite.getCases());
            sortedCases.sort(Comparator.comparing(EvalCase::getId));
            return suite.withCases(sortedCases);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    private void validateSuite(EvalSuite suite) {
        if (suite == null) {
            fail("suite must not be null");
        }
        requireText(suite.getSchemaVersion(), "schemaVersion");
        requireText(suite.getSuiteId(), "suite id");
        requireText(suite.getDatasetVersion(), "datasetVersion");
        if (suite.getCases() == null) {
            fail("cases must not be null");
        }

        Set<String> caseIds = new HashSet<String>();
        for (EvalCase evalCase : suite.getCases()) {
            validateCase(evalCase, caseIds);
        }
    }

    private void validateCase(EvalCase evalCase, Set<String> caseIds) {
        if (evalCase == null) {
            fail("case must not be null");
        }
        requireText(evalCase.getId(), "case id");
        if (!caseIds.add(evalCase.getId())) {
            fail("case id must be unique: " + evalCase.getId());
        }
        requireText(evalCase.getTitle(), "case title");
        requireValue(evalCase.getSplit(), "case split");
        requireValue(evalCase.getOrigin(), "case origin");
        requireValue(evalCase.getRiskLevel(), "case riskLevel");
        requireText(evalCase.getReviewStatus(), "reviewStatus");
        requireText(evalCase.getReviewerId(), "reviewerId");
        requireText(evalCase.getSourceCategory(), "sourceCategory");
        requireText(evalCase.getDifficultyReason(), "difficultyReason");
        requireText(evalCase.getFirstExposedDatasetVersion(), "firstExposedDatasetVersion");
        validateStringList(evalCase.getTags(), "tags");
        validateMessages(evalCase.getInputMessages());
        validateSubjectRefs(evalCase.getExpectedSubjects(), "expectedSubjects");
        requireList(evalCase.getAllowedResolutions(), "resolution");
        requireList(evalCase.getAllowedAnswerScopes(), "answerScope");
        validateStringList(evalCase.getRequiredClaimIds(), "requiredClaimIds");
        validateStringList(evalCase.getAllowedEvidenceIds(), "allowedEvidenceIds");
        validateStringList(evalCase.getForbiddenSubjectSlugs(), "forbiddenSubjectSlugs");
        validateStringList(evalCase.getForbiddenBehaviors(), "forbiddenBehaviors");
        validateLayers(evalCase);
        validateGraders(evalCase.getGraders());
        validateSubjectRefs(evalCase.getMaintenanceSubjects(), "maintenance subjectRefs");
        if (evalCase.getGeneratedFromBundleValue() == null) {
            fail("generatedFromBundle must be specified");
        }

        if (evalCase.getSplit() == EvalSplit.CHALLENGE && !externalChallengeSource) {
            fail("CHALLENGE cases require external source mode");
        }
    }

    private void validateMessages(List<EvalMessage> messages) {
        requireNonEmptyList(messages, "input messages");
        for (EvalMessage message : messages) {
            if (message == null) {
                fail("input message must not be null");
            }
            requireText(message.getRole(), "message role");
            requireText(message.getContent(), "message content");
        }
    }

    private void validateSubjectRefs(List<EvalSubjectRef> refs, String name) {
        requireList(refs, name);
        for (EvalSubjectRef ref : refs) {
            if (ref == null) {
                fail(name + " must not contain null");
            }
            requireValue(ref.getType(), name + " type");
            requireText(ref.getSlug(), name + " slug");
        }
    }

    private void validateLayers(EvalCase evalCase) {
        requireNonEmptyList(evalCase.getLayers(), "layers");
        if (evalCase.getProviderTrials() < 1) {
            fail("providerTrials must be at least 1");
        }
    }

    private void validateGraders(List<EvalGraderRule> graders) {
        requireNonEmptyList(graders, "graders");
        for (EvalGraderRule grader : graders) {
            if (grader == null) {
                fail("grader must not be null");
            }
            requireText(grader.getType(), "grader type");
            requireValue(grader.getSeverity(), "grader severity");
        }
    }

    private void validateStringList(List<String> values, String name) {
        requireList(values, name);
        for (String value : values) {
            requireText(value, name + " item");
        }
    }

    private void requireNonEmptyList(List<?> values, String name) {
        requireList(values, name);
        if (values.isEmpty()) {
            fail(name + " must not be empty");
        }
    }

    private void requireList(List<?> values, String name) {
        if (values == null) {
            fail(name + " must not be null");
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            fail(name + " must not be blank");
        }
    }

    private void requireValue(Object value, String name) {
        if (value == null) {
            fail(name + " must not be null");
        }
    }

    private void fail(String message) {
        throw new IllegalArgumentException(message);
    }

    private IllegalArgumentException invalid(Exception cause) {
        return new IllegalArgumentException("Invalid evaluation suite", cause);
    }
}
