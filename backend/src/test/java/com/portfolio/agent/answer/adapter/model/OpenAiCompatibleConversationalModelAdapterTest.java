package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    private OpenAiCompatibleConversationalModelAdapter adapter(RestClient.Builder builder) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ModelProviderDescriptor descriptor = new ModelProviderDescriptor(
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
        return new OpenAiCompatibleConversationalModelAdapter(
                builder,
                objectMapper,
                new ConversationalPromptFactory(objectMapper, "system prompt"),
                descriptor,
                "test-key",
                1200);
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
}
