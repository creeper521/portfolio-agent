package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.model.ModelProviderCapability;
import com.portfolio.agent.answer.adapter.model.ModelProviderDescriptor;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoalInterpretationAdapterTest {

    @Test
    void sendsOnlyGoalLevelAuthorityAndDecodesStrictProposal() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoalInterpretationAdapter adapter = adapter(builder);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(containsString("allowedGoalKinds")))
                .andExpect(content().string(containsString("publicSubjects")))
                .andExpect(content().string(not(containsString("taskType"))))
                .andExpect(content().string(not(containsString("dependencies"))))
                .andRespond(withSuccess(providerResponse("""
                        {"kind":"GOALS","goals":[{
                          "goalKey":"general-goal","goalKind":"GENERAL_EXPLANATION",
                          "inputAnchor":{"text":"解释幂等","start":0},
                          "subjectCandidates":[],"requestedOutputs":["EXPLANATION"],
                          "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
                          "parameters":{"kind":"GENERAL_EXPLANATION",
                            "topicAnchor":{"text":"幂等","start":2},"depth":"STANDARD"}
                        }]}
                        """), MediaType.APPLICATION_JSON));

        GoalInterpretationResult result = adapter.interpret(input());

        assertThat(result.getKind()).isEqualTo(GoalInterpretationResult.Kind.GOALS);
        assertThat(result.getGoalProposal().orElseThrow().getGoals()).hasSize(1);
        server.verify();
    }

    private GoalInterpretationAdapter adapter(RestClient.Builder builder) {
        ObjectMapper mapper = new ObjectMapper();
        return new GoalInterpretationAdapter(
                builder, mapper, new GoalProposalCodec(),
                new ModelProviderDescriptor(
                        ModelProviderKind.DEEPSEEK_V4_FLASH,
                        "adapter-v1",
                        URI.create("https://provider.example/v1/chat/completions"),
                        "deepseek-chat",
                        Set.of("unused-policy"),
                        Set.of("unused-schema"),
                        EnumSet.allOf(ModelProviderCapability.class)),
                "test-key", 1200, event -> { });
    }

    private GoalInterpretationInput input() {
        return new GoalInterpretationInput(
                "解释幂等", List.of(),
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()));
    }

    private String providerResponse(String proposal) throws Exception {
        String escaped = new ObjectMapper().writeValueAsString(proposal);
        return "{\"choices\":[{\"message\":{\"content\":" + escaped + "}}]}";
    }
}
