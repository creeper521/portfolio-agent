package com.portfolio.agent.answer.routing.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TurnProposal;
import com.portfolio.agent.answer.routing.gateway.TurnInterpretationPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TurnProposalCodecTest {

    private final TurnProposalCodec codec = new TurnProposalCodec(new ObjectMapper());

    @Test
    void decodesOneCurrentInputTaskBoundToThePublicCatalog() {
        TurnInterpretationPort.TurnInterpretationResult result = codec.decode("""
                {"kind":"PROPOSE_EXECUTION","tasks":[{
                  "clientTaskKey":"task-a","taskType":"PORTFOLIO_FACT",
                  "inputAnchor":{"verbatimText":"project-a","occurrence":1},
                  "subjectCandidates":[{"subjectType":"PROJECT","subjectId":"project-a",
                    "basis":"EXPLICIT_INPUT",
                    "evidenceAnchor":{"verbatimText":"project-a","occurrence":1}}],
                  "requestedOutputs":["SUMMARY"]
                }]}""", input());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getProposal().orElseThrow().getTasks()).hasSize(1);
        assertThat(result.getProposal().orElseThrow().getTasks().getFirst().getSubjectCandidates())
                .singleElement()
                .extracting(TurnProposal.SubjectCandidate::getBasis)
                .isEqualTo(TurnProposal.SubjectBasis.EXPLICIT_INPUT);
    }

    @Test
    void rejectsHostileOrIncompatiblePayloadsWithoutEchoingThem() {
        List<String> payloads = List.of(
                "{\"kind\":\"CONVERSE\",\"conversationAct\":\"UNINTERPRETABLE\",\"extra\":true}",
                "{\"kind\":\"CONVERSE\",\"kind\":\"CONVERSE\",\"conversationAct\":\"UNINTERPRETABLE\"}",
                "{\"kind\":\"CONVERSE\",\"conversationAct\":\"UNKNOWN\"}",
                "{\"kind\":\"ASK_CLARIFICATION\",\"clarification\":{\"field\":\"SUBJECT\",\"inputAnchor\":{\"verbatimText\":\"project-a\",\"occurrence\":1}},\"tasks\":[]}",
                "{\"kind\":\"PROPOSE_EXECUTION\",\"tasks\":[{\"clientTaskKey\":\"task-a\",\"taskType\":\"PORTFOLIO_FACT\",\"inputAnchor\":{\"verbatimText\":\"project-a\",\"occurrence\":1},\"subjects\":[],\"requestedOutputs\":[\"SUMMARY\"]},{\"clientTaskKey\":\"task-a\",\"taskType\":\"PORTFOLIO_FACT\",\"inputAnchor\":{\"verbatimText\":\"project-a\",\"occurrence\":1},\"subjects\":[],\"requestedOutputs\":[\"SUMMARY\"]}]}",
                "{\"kind\":\"PROPOSE_EXECUTION\",\"tasks\":[{\"clientTaskKey\":\"task-a\",\"taskType\":\"PORTFOLIO_FACT\",\"inputAnchor\":{\"verbatimText\":\"project-a\",\"occurrence\":1},\"subjects\":[{\"subjectType\":\"PROJECT\",\"subjectId\":\"invented\"}],\"requestedOutputs\":[\"SUMMARY\"]}]}",
                "{\"kind\":\"PROPOSE_EXECUTION\",\"tasks\":[{\"clientTaskKey\":\"task-a\",\"taskType\":\"GENERAL_EXPLANATION\",\"inputAnchor\":{\"verbatimText\":\"missing\",\"occurrence\":1},\"subjects\":[],\"requestedOutputs\":[\"SUMMARY\"]}]}",
                "{\"kind\":\"PROPOSE_EXECUTION\",\"evidenceIds\":[\"e-1\"],\"tasks\":[]}");

        for (String payload : payloads) {
            TurnInterpretationPort.TurnInterpretationResult result = codec.decode(payload, input());
            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getProposal()).isEmpty();
            assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        }
    }

    @Test
    void decodesOnlyDependenciesWhoseLocalKeysBelongToThisProposal() {
        TurnInterpretationPort.TurnInterpretationResult result = codec.decode("""
                {"kind":"PROPOSE_EXECUTION","dependencies":[
                  {"fromClientTaskKey":"first","toClientTaskKey":"second","dependencyType":"ORDER_AFTER"}],"tasks":[
                  {"clientTaskKey":"first","taskType":"GENERAL_EXPLANATION",
                    "inputAnchor":{"verbatimText":"Explain","occurrence":1},"subjectCandidates":[],"requestedOutputs":["SUMMARY"]},
                  {"clientTaskKey":"second","taskType":"GENERAL_EXPLANATION",
                    "inputAnchor":{"verbatimText":"project-a","occurrence":1},"subjectCandidates":[],"requestedOutputs":["SUMMARY"]}]}
                """, input());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getProposal().orElseThrow().getDependencies()).singleElement()
                .extracting(TurnProposal.ProposalDependency::getFromClientTaskKey,
                        TurnProposal.ProposalDependency::getToClientTaskKey)
                .containsExactly("first", "second");
    }

    @Test
    void decodesTheClosedResponseModeWithoutAcceptingUnknownValues() {
        TurnInterpretationPort.TurnInterpretationResult result = codec.decode("""
                {"kind":"PROPOSE_EXECUTION","tasks":[{
                  "clientTaskKey":"explain","taskType":"GENERAL_EXPLANATION",
                  "inputAnchor":{"verbatimText":"Explain","occurrence":1},"subjectCandidates":[],
                  "requestedOutputs":["SUMMARY"],"responseMode":"CONCISE"}]}
                """, input());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getProposal().orElseThrow().getTasks().getFirst().getResponseMode())
                .isEqualTo(TurnProposal.ResponseMode.CONCISE);
    }

    @Test
    void decodesClosedFactFacetsForLaterServerCompilation() {
        TurnInterpretationPort.TurnInterpretationResult result = codec.decode("""
                {"kind":"PROPOSE_EXECUTION","tasks":[{
                  "clientTaskKey":"fact","taskType":"PORTFOLIO_FACT",
                  "inputAnchor":{"verbatimText":"project-a","occurrence":1},
                  "subjectCandidates":[{"subjectType":"PROJECT","subjectId":"project-a","basis":"EXPLICIT_INPUT",
                    "evidenceAnchor":{"verbatimText":"project-a","occurrence":1}}],
                  "requestedOutputs":["SUMMARY"],"facets":["RESPONSIBILITY"]}]}
                """, input());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getProposal().orElseThrow().getTasks().getFirst().getFacets())
                .containsExactly("RESPONSIBILITY");
    }

    @Test
    void decodesClosedRecommendationFiltersAndRequestedSize() {
        TurnInterpretationPort.TurnInterpretationResult result = codec.decode("""
                {"kind":"PROPOSE_EXECUTION","tasks":[{
                  "clientTaskKey":"recommend","taskType":"PORTFOLIO_RECOMMEND",
                  "inputAnchor":{"verbatimText":"Explain","occurrence":1},"subjectCandidates":[],
                  "requestedOutputs":["RECOMMENDATION"],"careerTrack":"FULL_STACK_ENGINEERING",
                  "capabilityFilters":["VUE","TYPESCRIPT"],"requestedSize":3}]}
                """, new TurnInterpretationPort.TurnInterpretationInput(
                        "Explain project-a", List.of(new SubjectReference(
                        SubjectType.PROJECT, "project-a", SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1")),
                        Set.of(SemanticTaskType.PORTFOLIO_RECOMMEND)));

        assertThat(result.isSuccessful()).isTrue();
        TurnProposal.TaskProposal task = result.getProposal().orElseThrow().getTasks().getFirst();
        assertThat(task.getCareerTrack()).contains("FULL_STACK_ENGINEERING");
        assertThat(task.getCapabilityFilters()).containsExactlyInAnyOrder("VUE", "TYPESCRIPT");
        assertThat(task.getRequestedSize()).contains(3);
    }

    private TurnInterpretationPort.TurnInterpretationInput input() {
        return new TurnInterpretationPort.TurnInterpretationInput(
                "Explain project-a", List.of(new SubjectReference(
                SubjectType.PROJECT, "project-a", SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1")),
                Set.of(SemanticTaskType.PORTFOLIO_FACT, SemanticTaskType.GENERAL_EXPLANATION));
    }
}
