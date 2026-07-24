package com.portfolio.agent.release.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalBenchmarkCaseLoaderTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void loadsSortedImmutableSuiteWithEveryCaseField() {
        RetrievalBenchmarkSuite suite = new RetrievalBenchmarkCaseLoader(mapper)
                .load(validSuiteBytes());

        assertThat(suite.getSuiteVersion()).isEqualTo("retrieval-benchmark-v2");
        assertThat(suite.getContentVersion()).isEqualTo("2026-07-23.1");
        assertThat(suite.getCases()).extracting(RetrievalBenchmarkCase::getCaseId)
                .containsExactly("sql-background-exact-01", "sql-background-paraphrase-01");
        RetrievalBenchmarkCase item = suite.getCases().getFirst();
        assertThat(item.getSplit()).isEqualTo(RetrievalBenchmarkSplit.CALIBRATION);
        assertThat(item.getCategory()).isEqualTo(RetrievalBenchmarkCategory.EXACT_TERM);
        assertThat(item.getSubjectType()).isEqualTo(ClaimSubjectType.PROJECT);
        assertThat(item.getSubjectSlug()).isEqualTo("sql-audit");
        assertThat(item.getQuery()).isEqualTo("What was wrong with the original SQL audit flow?");
        assertThat(item.getExpectedClaimIds()).containsExactly("claim-sql-audit-background");
        assertThat(item.getExpectedChunkIds()).containsExactly("chunk-sql-audit-background");
        assertThat(item.getExpectedDecision()).isEqualTo(RetrievalDecisionType.SUFFICIENT);
        assertThatThrownBy(() -> suite.getCases().add(item))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> item.getExpectedClaimIds().add("claim-extra"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> item.getExpectedChunkIds().add("chunk-extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsUnknownJsonField() {
        assertInvalid(validSuiteJson().replace("\"query\": \"What was wrong with the original SQL audit flow?\",",
                "\"query\": \"What was wrong with the original SQL audit flow?\", \"unknown\": true,"));
    }

    @Test
    void rejectsDuplicateCaseId() {
        assertInvalid(validSuiteJson().replace("sql-background-paraphrase-01", "sql-background-exact-01"));
    }

    @Test
    void rejectsBlankQuery() {
        assertInvalid(validSuiteJson().replace("What was wrong with the original SQL audit flow?", "  "));
    }

    @Test
    void rejectsSufficientCaseWithoutExpectedClaimIds() {
        assertInvalid(validSuiteJson().replace("\"expectedClaimIds\": [\"claim-sql-audit-background\"]",
                "\"expectedClaimIds\": []"));
    }

    @Test
    void rejectsUnsupportedCategory() {
        assertInvalid(validSuiteJson().replace("\"category\": \"EXACT_TERM\"",
                "\"category\": \"NOT_A_CATEGORY\""));
    }

    @Test
    void rejectsUnsupportedSplit() {
        assertInvalid(validSuiteJson().replace("\"split\": \"CALIBRATION\"",
                "\"split\": \"TRAINING\""));
    }

    @Test
    void rejectsInvalidSubjectType() {
        assertInvalid(validSuiteJson().replace("\"subjectType\": \"PROJECT\"",
                "\"subjectType\": \"TEAM\""));
    }

    @Test
    void rejectsBlankSubjectSlug() {
        assertInvalid(validSuiteJson().replace("\"subjectSlug\": \"sql-audit\"",
                "\"subjectSlug\": \" \""));
    }

    @Test
    void rejectsContentVersionOutsideDateRevisionFormat() {
        assertInvalid(validSuiteJson().replace("2026-07-23.1", "content-1"));
    }

    private void assertInvalid(String source) {
        assertThatThrownBy(() -> new RetrievalBenchmarkCaseLoader(mapper)
                .load(source.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] validSuiteBytes() {
        return validSuiteJson().getBytes(StandardCharsets.UTF_8);
    }

    private String validSuiteJson() {
        return """
                {
                  "suiteVersion": "retrieval-benchmark-v2",
                  "contentVersion": "2026-07-23.1",
                  "cases": [
                    {
                      "caseId": "sql-background-paraphrase-01",
                      "split": "HOLDOUT",
                      "category": "SEMANTIC_PARAPHRASE",
                      "subjectType": "PROJECT",
                      "subjectSlug": "sql-audit",
                      "query": "Which problem did the SQL audit process begin with?",
                      "expectedClaimIds": ["claim-sql-audit-background"],
                      "expectedChunkIds": ["chunk-sql-audit-background"],
                      "expectedDecision": "SUFFICIENT"
                    },
                    {
                      "caseId": "sql-background-exact-01",
                      "split": "CALIBRATION",
                      "category": "EXACT_TERM",
                      "subjectType": "PROJECT",
                      "subjectSlug": "sql-audit",
                      "query": "What was wrong with the original SQL audit flow?",
                      "expectedClaimIds": ["claim-sql-audit-background"],
                      "expectedChunkIds": ["chunk-sql-audit-background"],
                      "expectedDecision": "SUFFICIENT"
                    }
                  ]
                }
                """;
    }
}
