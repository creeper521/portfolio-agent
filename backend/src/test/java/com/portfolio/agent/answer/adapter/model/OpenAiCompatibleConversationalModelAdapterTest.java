package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.DurationBucket;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskClassification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCompatibleConversationalModelAdapterTest {

    @Test
    void sendsApprovedVisitorQuestionAsOneStructuredNonStreamingRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(containsString("visitor-question-sentinel")))
                .andExpect(content().string(containsString("\"stream\":false")))
                .andExpect(content().string(containsString("\"type\":\"json_object\"")))
                .andRespond(withSuccess(routeResponse(), MediaType.APPLICATION_JSON));

        ConversationModelResult<ConversationRoute> result = adapter.classify(
                "visitor-question-sentinel",
                window(),
                List.of());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue().getIntent())
                .isEqualTo(ConversationIntent.GENERAL_KNOWLEDGE);
        server.verify();
    }

    @Test
    void classifiesInvalidStructuredContentWithoutRetryingAnotherProvider() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}",
                        MediaType.APPLICATION_JSON));

        ConversationModelResult<ConversationRoute> result =
                adapter.classify("question", window(), List.of());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void classifiesPortfolioTaskUsingTheConstrainedStructuredContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andExpect(content().string(containsString("\\\"operation\\\":\\\"portfolio_task\\\"")))
                .andRespond(withSuccess(providerResponse(
                        "{\"boundaryIntent\":null,\"mode\":\"RECOMMENDATION\",\"conditions\":{\"careerTrack\":\"backend\",\"audienceRole\":\"interviewer\",\"capabilityCodes\":[\"rag\"],\"goal\":\"当前目标\",\"requestedSize\":null},\"refinement\":null,\"confidence\":0.91}"),
                        MediaType.APPLICATION_JSON));

        ConversationModelResult<PortfolioTaskClassification> result =
                adapter.classifyPortfolioTask("turn-1", "请根据我的情况处理", null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue().getMode()).isEqualTo(PortfolioTaskMode.RECOMMENDATION);
        assertThat(result.getValue().getConditions().hasRequestedSize()).isFalse();
        server.verify();
    }

    @Test
    void decodesUnsafePortfolioTaskBoundaryFromTheStructuredContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<DiagnosticEvent> events = new ArrayList<>();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder, events::add);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(
                        "{\"boundaryIntent\":\"UNSUPPORTED_OR_UNSAFE\",\"mode\":null,\"conditions\":{\"careerTrack\":null,\"audienceRole\":null,\"capabilityCodes\":[],\"goal\":null,\"requestedSize\":null},\"refinement\":null,\"confidence\":0.97}"),
                        MediaType.APPLICATION_JSON));

        ConversationModelResult<PortfolioTaskClassification> result =
                adapter.classifyPortfolioTask(
                        "turn-1", "semantic-unsafe-question-sentinel", null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue().getBoundaryIntent())
                .isEqualTo(com.portfolio.agent.answer.domain.ConversationIntent.UNSUPPORTED_OR_UNSAFE);
        assertThat(result.getValue().getMode()).isNull();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getFields()).containsOnlyKeys(
                    "provider.operation",
                    "event.outcome",
                    "duration.bucket",
                    "response.present");
            assertThat(event.getFields().toString())
                    .doesNotContain("semantic-unsafe-question-sentinel");
        });
        server.verify();
    }

    @Test
    void rejectsPortfolioTaskBoundaryOutsideTheClosedSet() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(
                        "{\"boundaryIntent\":\"GENERAL_KNOWLEDGE\",\"mode\":null,\"conditions\":{\"careerTrack\":null,\"audienceRole\":null,\"capabilityCodes\":[],\"goal\":null,\"requestedSize\":null},\"refinement\":null,\"confidence\":0.97}"),
                        MediaType.APPLICATION_JSON));

        ConversationModelResult<PortfolioTaskClassification> result =
                adapter.classifyPortfolioTask("turn-1", "question", null);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void rejectsPortfolioTaskResponseWithAnIllegalMode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(
                        "{\"mode\":\"SELECT_SQL\",\"conditions\":{\"careerTrack\":null,\"audienceRole\":null,\"capabilityCodes\":[],\"goal\":null,\"requestedSize\":null},\"refinement\":null,\"confidence\":0.91}"),
                        MediaType.APPLICATION_JSON));

        ConversationModelResult<PortfolioTaskClassification> result =
                adapter.classifyPortfolioTask("turn-1", "请根据我的情况处理", null);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void unwrapsSuggestedQuestionsFromJsonObject() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {
                              "content": "{\\"questions\\":[{\\"text\\":\\"如何验证实现？\\",\\"projectSlug\\":\\"sql-audit\\",\\"caseSlug\\":null,\\"facet\\":\\"VERIFICATION\\"}]}"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        ConversationModelResult<List<ConversationSuggestedQuestion>> result =
                adapter.suggest(route(), window(), List.of(), List.of());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getValue()).singleElement().satisfies(question -> {
            assertThat(question.getText()).isEqualTo("如何验证实现？");
            assertThat(question.getProjectSlug()).isEqualTo("sql-audit");
            assertThat(question.getFacet())
                    .isEqualTo(PortfolioKnowledgeFacet.VERIFICATION);
        });
        server.verify();
    }

    @Test
    void publishesOneClosedCompletedEventForEveryConversationOperation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<DiagnosticEvent> events = new ArrayList<>();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder, events::add);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(routeResponse(), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(
                        providerResponse("{\"calls\":[]}"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(
                        "{\"title\":\"Generated\",\"resolution\":\"ANSWERED\",\"blocks\":[]}"),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(
                        "{\"unsupportedBlockIndexes\":[],\"reasonCodes\":[]}"),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(
                        "{\"questions\":[]}"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(
                        "{\"summary\":\"Approved summary\"}"), MediaType.APPLICATION_JSON));

        adapter.classify("question", window(), List.of());
        adapter.planTools(
                "question",
                window(),
                route(),
                PortfolioGroundingContext.empty(),
                List.of(),
                List.of());
        adapter.generate("question", window(), route(), PortfolioGroundingContext.empty());
        adapter.review(List.of(), PortfolioGroundingContext.empty());
        adapter.suggest(route(), window(), List.of(), List.of());
        adapter.summarize(List.of());

        assertThat(events).extracting(event ->
                        event.getFields().get("provider.operation"))
                .containsExactly(
                        "CLASSIFY",
                        "PLAN_TOOLS",
                        "GENERATE",
                        "REVIEW",
                        "SUGGEST",
                        "SUMMARIZE");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getName()).isEqualTo("provider.call.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.DEBUG);
            assertThat(event.getFields()).containsOnlyKeys(
                    "provider.operation",
                    "event.outcome",
                    "duration.bucket",
                    "response.present");
            assertThat(event.getFields())
                    .containsEntry("event.outcome", "success")
                    .containsEntry("response.present", true);
            assertDurationBucket(event.getFields().get("duration.bucket"));
        });
        server.verify();
    }

    @Test
    void timeoutPublishesClosedFailureClassificationWithoutSensitiveProviderData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<DiagnosticEvent> events = new ArrayList<>();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder, events::add);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "provider request timed out",
                            new HttpTimeoutException("sensitive-timeout-detail"));
                });

        ConversationModelResult<ConversationDraft> result = adapter.generate(
                "question", window(), route(), PortfolioGroundingContext.empty());

        assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.TIMEOUT);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("provider.call.failed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields()).containsOnlyKeys(
                    "provider.operation",
                    "event.outcome",
                    "duration.bucket",
                    "response.present",
                    "failure.code");
            assertThat(event.getFields())
                    .containsEntry("provider.operation", "GENERATE")
                    .containsEntry("event.outcome", "failure")
                    .containsEntry("response.present", false)
                    .containsEntry("failure.code", "PROVIDER_TIMEOUT")
                    .doesNotContainKeys(
                            "provider.name",
                            "provider.url",
                            "provider.payload",
                            "exception.message");
            assertDurationBucket(event.getFields().get("duration.bucket"));
        });
        server.verify();
    }

    @Test
    void diagnosticPublisherFailureDoesNotChangeProviderResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DiagnosticEventPublisher throwingPublisher = event -> {
            throw new IllegalStateException("diagnostics unavailable");
        };
        OpenAiCompatibleConversationalModelAdapter adapter =
                adapter(builder, throwingPublisher);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(routeResponse(), MediaType.APPLICATION_JSON));

        ConversationModelResult<ConversationRoute> result =
                adapter.classify("question", window(), List.of());

        assertThat(result.isSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void requestBuildFailurePublishesStableClassificationWithoutCallingProvider() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<DiagnosticEvent> events = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ConversationalPromptFactory promptFactory = mock(ConversationalPromptFactory.class);
        when(promptFactory.intentPrompt(anyMap(), anyList()))
                .thenThrow(new IllegalStateException("sensitive prompt detail"));
        OpenAiCompatibleConversationalModelAdapter adapter =
                new OpenAiCompatibleConversationalModelAdapter(
                        builder,
                        objectMapper,
                        promptFactory,
                        descriptor(),
                        "test-key",
                        1200,
                        events::add);

        ConversationModelResult<ConversationRoute> result =
                adapter.classify("question", window(), List.of());

        assertThat(result.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.REQUEST_BUILD_FAILED);
        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getFields())
                        .containsEntry(
                                "failure.code",
                                "PROVIDER_REQUEST_BUILD_FAILED")
                        .containsEntry("response.present", false));
        server.verify();
    }

    @Test
    void classifyConversationAssemblyFailureIsARequestBuildFailureWithoutProviderCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<DiagnosticEvent> events = new ArrayList<>();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder, events::add);
        ConversationWindow failingWindow = mock(ConversationWindow.class);
        when(failingWindow.getSummary())
                .thenThrow(new IllegalStateException("sensitive conversation detail"));

        ConversationModelResult<ConversationRoute> result =
                adapter.classify("question", failingWindow, List.of());

        assertThat(result.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.REQUEST_BUILD_FAILED);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("provider.call.failed");
            assertThat(event.getFields())
                    .containsEntry("provider.operation", "CLASSIFY")
                    .containsEntry(
                            "failure.code",
                            "PROVIDER_REQUEST_BUILD_FAILED")
                    .containsEntry("response.present", false)
                    .doesNotContainKey("provider.id");
        });
        server.verify();
    }

    @Test
    void emptyResponsePublishesExactlyOneClosedFailureEvent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<DiagnosticEvent> events = new ArrayList<>();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder, events::add);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse(""), MediaType.APPLICATION_JSON));

        ConversationModelResult<ConversationDraft> result = adapter.generate(
                "question", window(), route(), PortfolioGroundingContext.empty());

        assertThat(result.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.EMPTY_RESPONSE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("provider.call.failed");
            assertThat(event.getFields())
                    .containsEntry("failure.code", "PROVIDER_EMPTY_RESPONSE")
                    .containsEntry("response.present", false)
                    .doesNotContainKey("provider.id");
        });
        server.verify();
    }

    @Test
    void jsonNullPublishesExactlyOneInvalidResponseFailureEvent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<DiagnosticEvent> events = new ArrayList<>();
        OpenAiCompatibleConversationalModelAdapter adapter = adapter(builder, events::add);
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andRespond(withSuccess(providerResponse("null"), MediaType.APPLICATION_JSON));

        ConversationModelResult<ConversationDraft> result = adapter.generate(
                "question", window(), route(), PortfolioGroundingContext.empty());

        assertThat(result.getFailureCode())
                .isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("provider.call.failed");
            assertThat(event.getFields())
                    .containsEntry("failure.code", "PROVIDER_INVALID_RESPONSE")
                    .containsEntry("response.present", true)
                    .doesNotContainKey("provider.id");
        });
        server.verify();
    }

    private OpenAiCompatibleConversationalModelAdapter adapter(RestClient.Builder builder) {
        return adapter(builder, event -> { });
    }

    private OpenAiCompatibleConversationalModelAdapter adapter(
            RestClient.Builder builder,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new OpenAiCompatibleConversationalModelAdapter(
                builder,
                objectMapper,
                new ConversationalPromptFactory(objectMapper, "system prompt"),
                descriptor(),
                "test-key",
                1200,
                diagnosticEventPublisher);
    }

    private ModelProviderDescriptor descriptor() {
        return new ModelProviderDescriptor(
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                "conversation-v1",
                URI.create("https://provider.example/v1/chat/completions"),
                "chat-model",
                Set.of("model-policy.v1"),
                Set.of("conversation-answer.v2"),
                Set.of(
                        ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                        ModelProviderCapability.THINKING_CONTROL,
                        ModelProviderCapability.NON_STREAMING));
    }

    private ConversationWindow window() {
        return new ConversationWindow(
                null,
                List.of(new ConversationMessage(
                        ConversationMessageRole.USER, "earlier question")),
                10);
    }

    private ConversationRoute route() {
        return new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                0.98,
                "sql-audit",
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private String routeResponse() {
        return """
                {
                  "choices": [{
                    "message": {
                      "content": "{\\"intent\\":\\"GENERAL_KNOWLEDGE\\",\
\\"answerScope\\":\\"GENERAL\\",\\"confidence\\":0.98,\
\\"projectSlug\\":null,\\"caseSlug\\":null,\\"facet\\":\\"OVERVIEW\\",\
\\"clarificationRequired\\":false}"
                    }
                  }]
                }
                """;
    }

    private String providerResponse(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
    }

    private void assertDurationBucket(Object value) {
        assertThat(value).isInstanceOf(String.class);
        assertThat(java.util.Arrays.stream(DurationBucket.values())
                .map(DurationBucket::name))
                .contains((String) value);
    }
}
