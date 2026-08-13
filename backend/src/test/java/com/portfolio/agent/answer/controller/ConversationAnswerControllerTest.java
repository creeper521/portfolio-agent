package com.portfolio.agent.answer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.context.adapter.memory.InMemoryConversationBusinessContextStore;
import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.service.ProductionConversationService;
import com.portfolio.agent.answer.service.ProductionConversationExecution;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.RequestContextHolder;
import com.portfolio.agent.common.web.RequestDiagnosticsFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationAnswerControllerTest {

    private static final String TURN_ID = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";

    @Test
    void exposesV2ConversationContract() throws Exception {
        ProductionConversationService service = mock(ProductionConversationService.class);
        when(service.answer(any(), any())).thenAnswer(invocation -> {
            assertThat(RequestContextHolder.requireCurrent().getTurnId()).isEqualTo(TURN_ID);
            assertThat(MDC.get("turn.id")).isEqualTo(TURN_ID);
            return result();
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                service,
                                new ClientAddressResolver(false, java.util.Set.of()),
                                new ConversationAnswerResponseMapper()))
                .addFilters(new RequestDiagnosticsFilter(event -> { }))
                .build();
        String request = """
                {
	                  "turnId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
	                  "requestToken": "63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "question": "你好",
                  "messages": [],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE"
                  }
                }
                """;

        mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CONVERSATION"))
                .andExpect(jsonPath("$.answerScope").value("GLOBAL"))
                .andExpect(jsonPath("$.blocks[0].sourceScope").value("GENERAL"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.portfolioRecommendation").doesNotExist());

        verify(service).answer(any(), eq("127.0.0.1"));
    }

    @Test
    void createsAndReturnsAResumeTokenOnlyForTheFirstRequest() throws Exception {
        ProductionConversationService service = mock(ProductionConversationService.class);
        when(service.execute(any(), any(), any()))
                .thenReturn(ProductionConversationExecution.answer(result()));
        InMemoryConversationBusinessContextStore contextStore =
                new InMemoryConversationBusinessContextStore();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                service,
                                new ClientAddressResolver(false, java.util.Set.of()),
                                new ConversationAnswerResponseMapper(),
                                Optional.of(contextStore)))
                .addFilters(new RequestDiagnosticsFilter(event -> { }))
                .build();
        String request = """
                {
                  "turnId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
                  "requestToken": "63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "question": "hello",
                  "messages": []
                }
                """;

        String token = mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.continuationStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.conversation.resumeToken").isString())
                .andReturn().getResponse().getContentAsString();
        String encodedToken = new ObjectMapper().readTree(token)
                .path("conversation").path("resumeToken").asText();

        mvc.perform(post("/api/v2/answers")
                        .header("X-Conversation-Resume-Token", encodedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.continuationStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.conversation.resumeToken").doesNotExist());
    }

    @Test
    void recoversACompletedFirstRequestByRotatingTheResumeToken() throws Exception {
        ProductionConversationService service = mock(ProductionConversationService.class);
        InMemoryConversationBusinessContextStore contextStore =
                new InMemoryConversationBusinessContextStore();
        ConversationId conversationId = ConversationId.random();
        ResumeToken oldToken = ResumeToken.issue();
        contextStore.open(conversationId, oldToken, java.time.Instant.now());
        com.portfolio.agent.answer.context.domain.ContextHandle handle =
                com.portfolio.agent.answer.context.domain.ContextHandle.issue();
        CompletionReceipt receipt = new CompletionReceipt(
                java.util.UUID.fromString("63f63c75-16e8-49e7-864d-dcd0fe100d50"),
                conversationId,
                RequestFingerprint.sha256Canonical("same-request"),
                handle,
                ConversationContinuationStatus.AVAILABLE,
                java.time.Instant.now());
        when(service.findCompleted(any())).thenReturn(Optional.of(receipt));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                service,
                                new ClientAddressResolver(false, java.util.Set.of()),
                                new ConversationAnswerResponseMapper(),
                                Optional.of(contextStore)))
                .addFilters(new RequestDiagnosticsFilter(event -> { }))
                .build();

        String body = """
                {
                  "turnId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
                  "requestToken": "63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "question": "hello",
                  "messages": []
                }
                """;
        String response = mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseKind").value("COMPLETION_RECEIPT"))
                .andExpect(jsonPath("$.completedTasks[0].contextHandle")
                        .value(handle.asBase64Url()))
                .andExpect(jsonPath("$.conversation.resumeToken").isString())
                .andReturn().getResponse().getContentAsString();
        String replacement = new ObjectMapper().readTree(response)
                .path("conversation").path("resumeToken").asText();
        assertThat(contextStore.findConversation(oldToken)).isEmpty();
        assertThat(contextStore.findConversation(ResumeToken.fromBase64Url(replacement)))
                .contains(conversationId);
    }

    @Test
    void legacyAnswerEndpointIsNotRegistered() throws Exception {
        ProductionConversationService service = mock(ProductionConversationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                service,
                                new ClientAddressResolver(false, java.util.Set.of()),
                                new ConversationAnswerResponseMapper()))
                .build();

        mvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    @Test
    void missingRequestContextFailsBeforeCallingTheService() {
        RequestContextHolder.clear();
        ProductionConversationService service = mock(ProductionConversationService.class);
        ConversationAnswerController controller = new ConversationAnswerController(
                service,
                new ClientAddressResolver(false, java.util.Set.of()),
                new ConversationAnswerResponseMapper());
        ConversationAnswerRequest request = mock(ConversationAnswerRequest.class);

        assertThatThrownBy(() -> controller.answer(request, new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("request context is not available");
        verifyNoInteractions(service);
    }

    @Test
    void doesNotExposeRecommendationContextWhenRecommendationListsAreEmpty() throws Exception {
        ProductionConversationService service = mock(ProductionConversationService.class);
        when(service.answer(any(), any())).thenReturn(recommendationResult());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                service,
                                new ClientAddressResolver(false, java.util.Set.of()),
                                new ConversationAnswerResponseMapper()))
                .addFilters(new RequestDiagnosticsFilter(event -> { }))
                .build();

        mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
                                  "requestToken": "63f63c75-16e8-49e7-864d-dcd0fe100d50",
                                  "question": "推荐作品集",
                                  "messages": [],
                                  "context": { "audienceRole": "INTERVIEWER", "source": "AGENT_PAGE" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioRecommendation.recommendationBatchId")
                        .value("rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
                .andExpect(jsonPath("$.portfolioRecommendation.context").doesNotExist())
                .andExpect(jsonPath("$.portfolioRecommendation.items").isArray())
                .andExpect(jsonPath("$.portfolioRecommendation.items").isEmpty())
                .andExpect(jsonPath("$.portfolioRecommendation.satisfiedConstraints").isEmpty())
                .andExpect(jsonPath("$.portfolioRecommendation.unsatisfiedConstraints").isEmpty());
    }

    @Test
    void rejectsInvalidRegenerateActionAtHttpBoundary() throws Exception {
        ProductionConversationService service = mock(ProductionConversationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                service,
                                new ClientAddressResolver(false, java.util.Set.of()),
                                new ConversationAnswerResponseMapper()))
                .setValidator(validator)
                .build();

        mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
                                  "requestToken": "63f63c75-16e8-49e7-864d-dcd0fe100d50",
                                  "action": "REGENERATE_PLAN",
                                  "question": "regenerate",
                                  "messages": [],
                                  "invalidatedPlanReference": {
                                    "planId": "plan-1",
                                    "planFingerprint": "sha256:value"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private ConversationAnswerResult result() {
        return new ConversationAnswerResult(
                TURN_ID,
                "v1",
                ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION,
                AnswerResolution.ANSWERED,
                "你好",
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL,
                        "你好，我可以聊通用技术，也可以介绍作品集。",
                        List.of(),
                        List.of())),
                List.of(),
                false);
    }

    private ConversationAnswerResult recommendationResult() {
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "public-2026-07-31", "BACKEND", "INTERVIEWER", Set.of("RAG"), 2,
                List.of());
        PortfolioRecommendation recommendation = new PortfolioRecommendation(
                context.getRecommendationBatchId(), context, List.of(), List.of(), List.of());
        return new ConversationAnswerResult(
                TURN_ID, "public-2026-07-31", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "推荐", List.of(),
                List.of(), false, com.portfolio.agent.answer.domain.GenerationMode.DETERMINISTIC,
                null, null, new com.portfolio.agent.answer.domain.ConversationProgress(
                        List.of(), com.portfolio.agent.answer.domain.ConversationGuidanceStage.OPENING),
                recommendation);
    }
}
