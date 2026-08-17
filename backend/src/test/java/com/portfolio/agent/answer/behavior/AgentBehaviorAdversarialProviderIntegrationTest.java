package com.portfolio.agent.answer.adapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.gateway.SemanticClassifierPort;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

/**
 * L3 provider seam checks. The server is deliberately local and deterministic;
 * no test in this class can send a request to a real provider.
 */
class AgentBehaviorAdversarialProviderIntegrationTest {

    @Test
    void timeoutDoesNotBecomeAnAnswerAndAFollowingTurnCanRecover() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo(endpoint())).andRespond(request -> {
            throw new ResourceAccessException("provider timeout",
                    new HttpTimeoutException("synthetic timeout"));
        });
        server.expect(once(), requestTo(endpoint()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recovery-turn")))
                .andRespond(withSuccess(routeResponse(), MediaType.APPLICATION_JSON));

        ConversationModelResult<ConversationRoute> failed = adapter.classify(
                "timeout-turn", window(), List.of());
        ConversationModelResult<ConversationRoute> recovered = adapter.classify(
                "recovery-turn", window(), List.of());

        assertThat(failed.isSuccessful()).isFalse();
        assertThat(failed.getFailureCode()).isEqualTo(ConversationModelFailureCode.TIMEOUT);
        assertThat(recovered.isSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void unavailableProviderIsAClosedFailureWithoutRetryingOrLeakingPayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo(endpoint()))
                .andRespond(withServerError());

        ConversationModelResult<ConversationRoute> result = adapter.classify(
                "unavailable-turn", window(), List.of());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.PROVIDER_ERROR);
        server.verify();
    }

    @Test
    void malformedProviderDraftIsRejectedAndDoesNotProduceAUsableRoute() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo(endpoint())).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"intent\\\":\\\"INVENTED\\\"}\"}}]}",
                MediaType.APPLICATION_JSON));

        ConversationModelResult<ConversationRoute> result = adapter.classify(
                "invalid-draft-turn", window(), List.of());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void cancelledTransportIsClosedAsProviderFailureAndCannotBeRetriedImplicitly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo(endpoint())).andRespond(request -> {
            throw new ResourceAccessException("synthetic cancellation", new java.io.IOException("cancelled"));
        });

        ConversationModelResult<ConversationRoute> result = adapter.classify(
                "cancelled-turn", window(), List.of());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.PROVIDER_ERROR);
        server.verify();
    }

    @Test
    void semanticRoutingProviderPayloadIsDecodedThroughTheClosedCandidateContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo(endpoint()))
                .andRespond(withSuccess(providerResponse(
                        "{\"taskCandidates\":[],\"dependencyCandidates\":[],\"exclusionCandidates\":[]}"),
                        MediaType.APPLICATION_JSON));

        SemanticClassifierPort.SemanticClassificationResult result = adapter.classify(
                new SemanticClassifierPort.SemanticClassificationInput(
                        "112233",
                        List.of(new SubjectReference(
                                SubjectType.PROJECT, "public-project",
                                SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1"))));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTaskCandidates()).isEmpty();
        assertThat(result.getDependencyCandidates()).isEmpty();
        assertThat(result.getExclusionCandidates()).isEmpty();
        server.verify();
    }

    private OpenAiCompatibleConversationalModelAdapter adapter(RestClient.Builder builder) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new OpenAiCompatibleConversationalModelAdapter(
                builder,
                objectMapper,
                new ConversationalPromptFactory(objectMapper, "synthetic system prompt"),
                descriptor(),
                "synthetic-key",
                600,
                event -> { });
    }

    private ModelProviderDescriptor descriptor() {
        return new ModelProviderDescriptor(
                com.portfolio.agent.answer.domain.ModelProviderKind.DEEPSEEK_V4_FLASH,
                "behavior-test-v1",
                URI.create(endpoint()),
                "synthetic-model",
                Set.of("synthetic-policy-v1"),
                Set.of("conversation.answer.v2"),
                Set.of(ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                        ModelProviderCapability.THINKING_CONTROL,
                        ModelProviderCapability.NON_STREAMING));
    }

    private ConversationWindow window() {
        return new ConversationWindow(
                null,
                List.of(new ConversationMessage(
                        ConversationMessageRole.USER, "synthetic prior turn")),
                10);
    }

    private String routeResponse() {
        return """
                {
                  "choices": [{
                    "message": {
                      "content": "{\\\"intent\\\":\\\"GENERAL_KNOWLEDGE\\\",\\\"answerScope\\\":\\\"GENERAL\\\",\\\"confidence\\\":0.98,\\\"projectSlug\\\":null,\\\"caseSlug\\\":null,\\\"facet\\\":\\\"OVERVIEW\\\",\\\"clarificationRequired\\\":false}"
                    }
                  }]
                }
                """;
    }

    private String providerResponse(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
    }

    private static String endpoint() {
        return "https://provider.example/v1/chat/completions";
    }
}
