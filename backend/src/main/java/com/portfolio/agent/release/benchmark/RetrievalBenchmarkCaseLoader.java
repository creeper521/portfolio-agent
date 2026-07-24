package com.portfolio.agent.release.benchmark;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class RetrievalBenchmarkCaseLoader {

    private static final String SUITE_VERSION = "retrieval-benchmark-v2";
    private static final Pattern CONTENT_VERSION = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\.\\d+");

    private final ObjectMapper mapper;

    public RetrievalBenchmarkCaseLoader(ObjectMapper mapper) {
        this.mapper = mapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public RetrievalBenchmarkSuite load(byte[] source) {
        try {
            RetrievalBenchmarkSuite decoded = mapper.readValue(source, RetrievalBenchmarkSuite.class);
            return validateAndSort(decoded);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid retrieval benchmark suite", exception);
        }
    }

    private RetrievalBenchmarkSuite validateAndSort(RetrievalBenchmarkSuite suite) {
        require(SUITE_VERSION.equals(suite.getSuiteVersion()), "suiteVersion is invalid");
        require(CONTENT_VERSION.matcher(suite.getContentVersion()).matches(),
                "contentVersion must match YYYY-MM-DD.N");
        require(!suite.getCases().isEmpty(), "cases must not be empty");

        Set<String> caseIds = new HashSet<>();
        for (RetrievalBenchmarkCase item : suite.getCases()) {
            validateCase(item, caseIds);
        }

        List<RetrievalBenchmarkCase> sortedCases = new ArrayList<>(suite.getCases());
        sortedCases.sort(Comparator.comparing(RetrievalBenchmarkCase::getCaseId));
        return new RetrievalBenchmarkSuite(suite.getSuiteVersion(), suite.getContentVersion(), sortedCases);
    }

    private void validateCase(RetrievalBenchmarkCase item, Set<String> caseIds) {
        require(item != null, "case is required");
        require(hasText(item.getCaseId()), "caseId is required");
        require(caseIds.add(item.getCaseId()), "caseId must be unique: " + item.getCaseId());
        require(item.getSplit() != null, "split is required: " + item.getCaseId());
        require(item.getCategory() != null, "category is required: " + item.getCaseId());
        require(item.getSubjectType() != null, "subjectType is required: " + item.getCaseId());
        require(hasText(item.getSubjectSlug()), "subjectSlug is required: " + item.getCaseId());
        require(hasText(item.getQuery()), "query is required: " + item.getCaseId());
        require(item.getExpectedDecision() != null,
                "expectedDecision is required: " + item.getCaseId());
        validateNonBlankValues(item.getExpectedClaimIds(), "expectedClaimIds", item.getCaseId());
        validateNonBlankValues(item.getExpectedChunkIds(), "expectedChunkIds", item.getCaseId());
        if (item.getExpectedDecision() == RetrievalDecisionType.SUFFICIENT) {
            require(!item.getExpectedClaimIds().isEmpty(),
                    "SUFFICIENT case requires expectedClaimIds: " + item.getCaseId());
        }
    }

    private void validateNonBlankValues(List<String> values, String field, String caseId) {
        require(values != null, field + " is required: " + caseId);
        for (String value : values) {
            require(hasText(value), field + " must not contain blanks: " + caseId);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
