package com.portfolio.agent.answer.routing.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.gateway.SemanticClassifierPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticClassificationCodecTest {

    @Test
    void acceptsOnlyClosedCandidatesWhoseSubjectsAppearInThePublicCatalog() {
        SemanticClassificationCodec codec = new SemanticClassificationCodec(new ObjectMapper());
        SemanticClassifierPort.SemanticClassificationInput input = input();

        SemanticClassifierPort.SemanticClassificationResult result = codec.decode("""
                {
                  "taskCandidates": [{
                    "taskType": "PORTFOLIO_FACT",
                    "questionSpan": "介绍 project-a",
                    "subjects": [{"subjectType": "PROJECT", "subjectId": "project-a"}],
                    "dimensions": ["ARCHITECTURE"],
                    "requestedOutputs": ["SUMMARY"]
                  }],
                  "dependencyCandidates": [],
                  "exclusionCandidates": []
                }
                """, input);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTaskCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getTaskType()).isEqualTo(SemanticTaskType.PORTFOLIO_FACT);
            assertThat(candidate.getSubjects()).containsExactly(new SubjectReference(
                    SubjectType.PROJECT, "project-a", SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE,
                    "public-v1"));
        });
    }

    @Test
    void failsClosedForAnInventedTaskTypeOrPublicSubject() {
        SemanticClassificationCodec codec = new SemanticClassificationCodec(new ObjectMapper());

        SemanticClassifierPort.SemanticClassificationResult inventedTask = codec.decode("""
                {"taskCandidates":[{"taskType":"DELETE_REPOSITORY","questionSpan":"x",
                  "subjects":[],"dimensions":[],"requestedOutputs":[]}],
                  "dependencyCandidates":[],"exclusionCandidates":[]}
                """, input());
        SemanticClassifierPort.SemanticClassificationResult inventedSubject = codec.decode("""
                {"taskCandidates":[{"taskType":"PORTFOLIO_FACT","questionSpan":"x",
                  "subjects":[{"subjectType":"PROJECT","subjectId":"private-project"}],
                  "dimensions":[],"requestedOutputs":[]}],
                  "dependencyCandidates":[],"exclusionCandidates":[]}
                """, input());

        assertThat(inventedTask.isSuccessful()).isFalse();
        assertThat(inventedTask.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        assertThat(inventedSubject.isSuccessful()).isFalse();
        assertThat(inventedSubject.getTaskCandidates()).isEmpty();
    }

    @Test
    void failsClosedForMalformedOrUndeclaredFields() {
        SemanticClassificationCodec codec = new SemanticClassificationCodec(new ObjectMapper());

        SemanticClassifierPort.SemanticClassificationResult malformed = codec.decode("{", input());
        SemanticClassifierPort.SemanticClassificationResult hostile = codec.decode("""
                {"taskCandidates":[],"dependencyCandidates":[],"exclusionCandidates":[],
                  "providerParameters":{"leak":"not allowed"}}
                """, input());

        assertThat(malformed.isSuccessful()).isFalse();
        assertThat(hostile.isSuccessful()).isFalse();
        assertThat(hostile.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
    }

    @Test
    void treatsNullProviderPayloadAsAnInvalidResponse() {
        SemanticClassificationCodec codec = new SemanticClassificationCodec(new ObjectMapper());

        SemanticClassifierPort.SemanticClassificationResult result = codec.decode((String) null, input());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
    }

    @Test
    void acceptsEveryTypedPlanExclusionValueAndRejectsMismatchedValues() {
        SemanticClassificationCodec codec = new SemanticClassificationCodec(new ObjectMapper());
        String exclusions = """
                [
                  {"scope":"PLAN","exclusionType":"SUBJECT","subject":{"subjectType":"PROJECT","subjectId":"project-a"}},
                  {"scope":"PLAN","exclusionType":"OUTPUT","requestedOutput":"RECOMMENDATION"},
                  {"scope":"PLAN","exclusionType":"DIMENSION","dimension":"ARCHITECTURE"},
                  {"scope":"PLAN","exclusionType":"CONSTRAINT","constraint":"EXCLUDE_UNVERIFIED"}
                ]
                """;
        SemanticClassifierPort.SemanticClassificationResult accepted = codec.decode(
                "{\"taskCandidates\":[],\"dependencyCandidates\":[],\"exclusionCandidates\":"
                        + exclusions + "}", input());
        SemanticClassifierPort.SemanticClassificationResult mismatched = codec.decode("""
                {"taskCandidates":[],"dependencyCandidates":[],"exclusionCandidates":[
                  {"scope":"PLAN","exclusionType":"SUBJECT","requestedOutput":"SUMMARY"}
                ]}
                """, input());

        assertThat(accepted.isSuccessful()).isTrue();
        assertThat(accepted.getExclusionCandidates()).hasSize(4);
        assertThat(mismatched.isSuccessful()).isFalse();
        assertThat(mismatched.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);

        List<String> crossTypedValues = List.of(
                "\"exclusionType\":\"SUBJECT\",\"subject\":{\"subjectType\":\"PROJECT\",\"subjectId\":\"project-a\"},\"requestedOutput\":\"SUMMARY\"",
                "\"exclusionType\":\"OUTPUT\",\"requestedOutput\":\"SUMMARY\",\"dimension\":\"ARCHITECTURE\"",
                "\"exclusionType\":\"DIMENSION\",\"dimension\":\"ARCHITECTURE\",\"constraint\":\"EXCLUDE_UNVERIFIED\"",
                "\"exclusionType\":\"CONSTRAINT\",\"constraint\":\"EXCLUDE_UNVERIFIED\",\"subject\":{\"subjectType\":\"PROJECT\",\"subjectId\":\"project-a\"}"
        );
        for (String crossTypedValue : crossTypedValues) {
            SemanticClassifierPort.SemanticClassificationResult crossTyped = codec.decode(
                    "{\"taskCandidates\":[],\"dependencyCandidates\":[],\"exclusionCandidates\":[{"
                            + "\"scope\":\"PLAN\"," + crossTypedValue + "}]}",
                    input());
            assertThat(crossTyped.isSuccessful()).isFalse();
            assertThat(crossTyped.getFailureCode())
                    .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        }

        List<String> blankCrossTypedValues = List.of(
                "\"exclusionType\":\"SUBJECT\",\"subject\":{\"subjectType\":\"PROJECT\",\"subjectId\":\"project-a\"},\"requestedOutput\":\" \"",
                "\"exclusionType\":\"OUTPUT\",\"requestedOutput\":\"SUMMARY\",\"dimension\":\"\"",
                "\"exclusionType\":\"DIMENSION\",\"dimension\":\"ARCHITECTURE\",\"constraint\":\"  \"",
                "\"exclusionType\":\"CONSTRAINT\",\"constraint\":\"EXCLUDE_UNVERIFIED\",\"subject\":null"
        );
        for (String blankCrossTypedValue : blankCrossTypedValues) {
            SemanticClassifierPort.SemanticClassificationResult blankCrossTyped = codec.decode(
                    "{\"taskCandidates\":[],\"dependencyCandidates\":[],\"exclusionCandidates\":[{"
                            + "\"scope\":\"PLAN\"," + blankCrossTypedValue + "}]}",
                    input());
            assertThat(blankCrossTyped.isSuccessful()).isFalse();
            assertThat(blankCrossTyped.getFailureCode())
                    .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        }
    }

    @Test
    void returnsImmutableValueSemanticCandidatesWithoutProviderDiagnostics() {
        SemanticClassificationCodec codec = new SemanticClassificationCodec(new ObjectMapper());

        SemanticClassifierPort.SemanticClassificationResult first = codec.decode("""
                {"taskCandidates":[],"dependencyCandidates":[],"exclusionCandidates":[]}
                """, input());
        SemanticClassifierPort.SemanticClassificationResult second = codec.decode("""
                {"taskCandidates":[],"dependencyCandidates":[],"exclusionCandidates":[]}
                """, input());

        assertThat(first).isEqualTo(second);
        assertThat(first.getTaskCandidates()).isUnmodifiable();
        assertThat(first.toString()).doesNotContain("provider", "prompt", "payload");
    }

    private SemanticClassifierPort.SemanticClassificationInput input() {
        return new SemanticClassifierPort.SemanticClassificationInput(
                "介绍 project-a",
                List.of(new SubjectReference(
                        SubjectType.PROJECT,
                        "project-a",
                        SubjectResolutionSource.EXPLICIT_REFERENCE,
                        "public-v1")));
    }
}
