package com.portfolio.agent.answer.adapter.model;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.controller.ConversationAnswerController;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKeywordIndex;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.adapter.observability.LoggingConversationDecisionPublisher;
import com.portfolio.agent.answer.service.AnswerAdmissionGate;
import com.portfolio.agent.answer.service.AnswerIdempotencyCoordinator;
import com.portfolio.agent.answer.service.ConversationalAgentRuntime;
import com.portfolio.agent.answer.service.ConversationDraftValidator;
import com.portfolio.agent.answer.service.ConversationIntentRouter;
import com.portfolio.agent.answer.service.ConversationSubjectGuard;
import com.portfolio.agent.answer.service.ConversationToolService;
import com.portfolio.agent.answer.service.ConversationWindowManager;
import com.portfolio.agent.answer.service.DeterministicConversationFallback;
import com.portfolio.agent.answer.service.DynamicQuestionService;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;
import com.portfolio.agent.answer.service.LocalRetrievalCoordinator;
import com.portfolio.agent.answer.service.ProductionConversationService;
import com.portfolio.agent.answer.service.PortfolioGroundingAssembler;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DroppedDiagnosticCounter;
import com.portfolio.agent.common.observability.Slf4jDiagnosticEventPublisher;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.RequestDiagnosticsFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuntimeCompositePrivacyTest {

    private static final String QUESTION_SENTINEL =
            "visitor-question-sentinel-5ef3b1";
    private static final String HISTORY_SENTINEL =
            "visitor-history-sentinel-6a8c42";
    private static final String PROVIDER_RESPONSE_SENTINEL =
            "provider-response-sentinel-7bd953";
    private static final String RETRIEVAL_QUERY_SENTINEL =
            "retrieval-query-sentinel-8ce164";
    private static final String API_KEY_SENTINEL =
            "api-key-sentinel-9df275";
    private static final String RAW_IP_SENTINEL = "192.168.77.231";
    private static final String EXCEPTION_MESSAGE_SENTINEL =
            "exception-message-sentinel-ae0386";
    private static final String RENDERED_THROWABLE_SENTINEL =
            "rendered-throwable-sentinel-bf1497";
    private static final String RENDERED_MDC_SENTINEL =
            "rendered-mdc-sentinel-c025a8";

    @Test
    void renderedLogCaptureIncludesThrowableProxyAndMdc() {
        Logger logger = (Logger) LoggerFactory.getLogger(
                "com.portfolio.agent.diagnostics");
        boolean originalAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
        try {
            MDC.put("controlled.sentinel", RENDERED_MDC_SENTINEL);
            logger.warn(
                    "controlled rendered-log fixture",
                    new IllegalStateException(RENDERED_THROWABLE_SENTINEL));

            assertThat(renderCaptured(List.of(), appender.list))
                    .contains(
                            RENDERED_THROWABLE_SENTINEL,
                            RENDERED_MDC_SENTINEL);
        } finally {
            MDC.remove("controlled.sentinel");
            logger.detachAppender(appender);
            appender.stop();
            logger.setAdditive(originalAdditive);
        }
    }

    @Test
    void requestRuntimeDiagnosticsAndRenderedLogsNeverContainSensitiveSentinels()
            throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(
                "com.portfolio.agent.diagnostics");
        Logger rootLogger = (Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level originalLevel = logger.getLevel();
        boolean originalAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        List<DiagnosticEvent> events = new ArrayList<>();
        Slf4jDiagnosticEventPublisher loggingPublisher =
                new Slf4jDiagnosticEventPublisher(
                        logger, new DroppedDiagnosticCounter());
        DiagnosticEventPublisher collectingPublisher = event -> {
            events.add(event);
            loggingPublisher.publish(event);
        };
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
        rootLogger.addAppender(appender);
        try {
            RuntimeSeams seams = runtimeSeams(collectingPublisher);
            ProductionConversationService service = new ProductionConversationService(
                    seams.runtime(),
                    new AnonymousSourceHasher(),
                    new AnswerAdmissionGate(Clock.systemUTC(), 10, 2),
                    new AnswerIdempotencyCoordinator<>(
                            Clock.systemUTC(), Duration.ofMinutes(2)),
                    executor,
                    Duration.ofSeconds(5));
            MockMvc mvc = MockMvcBuilders.standaloneSetup(
                            new ConversationAnswerController(
                                    service,
                                    new ClientAddressResolver(false, Set.of()),
                                    new ConversationAnswerResponseMapper()))
                    .addFilters(new RequestDiagnosticsFilter(collectingPublisher))
                    .build();

            mvc.perform(post("/api/v2/answers")
                            .with(request -> {
                                request.setRemoteAddr(RAW_IP_SENTINEL);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson()))
                    .andExpect(status().isOk());

            seams.verify();
            String captured = renderCaptured(events, appender.list);
            assertThat(captured).doesNotContain(
                    QUESTION_SENTINEL,
                    HISTORY_SENTINEL,
                    PROVIDER_RESPONSE_SENTINEL,
                    RETRIEVAL_QUERY_SENTINEL,
                    API_KEY_SENTINEL,
                    RAW_IP_SENTINEL,
                    EXCEPTION_MESSAGE_SENTINEL);
            assertThat(events).extracting(DiagnosticEvent::getName)
                    .contains(
                            "http.request.started",
                            "provider.call.failed",
                            "retrieval.degraded",
                            "answer.validation.completed",
                            "answer.fallback.selected",
                            "agent.request.completed",
                            "http.request.completed");
        } finally {
            executor.close();
            rootLogger.detachAppender(appender);
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }
    }

    private RuntimeSeams runtimeSeams(DiagnosticEventPublisher publisher) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer " + API_KEY_SENTINEL))
                .andExpect(content().string(containsString(QUESTION_SENTINEL)))
                .andExpect(content().string(containsString(HISTORY_SENTINEL)))
                .andRespond(withSuccess(
                        providerResponse("""
                                {"calls":[{"kind":"GET_PROJECT",\
"projectSlugs":["sql-audit"],"caseSlugs":[],"claimIds":[],\
"sectionType":null}]}
                                """),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer " + API_KEY_SENTINEL))
                .andExpect(content().string(containsString(QUESTION_SENTINEL)))
                .andExpect(content().string(containsString(HISTORY_SENTINEL)))
                .andRespond(withSuccess(
                        providerResponse("""
                                {"title":"%s","resolution":"ANSWERED","blocks":[{\
"sourceScope":"PORTFOLIO","content":"Safe generated block",\
"claimIds":["claim-1"],"evidenceIds":["evidence-1"]}]}
                                """.formatted(PROVIDER_RESPONSE_SENTINEL)),
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://provider.example/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer " + API_KEY_SENTINEL))
                .andRespond(request -> {
                    throw new ResourceAccessException(EXCEPTION_MESSAGE_SENTINEL);
                });
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OpenAiCompatibleConversationalModelAdapter adapter =
                new OpenAiCompatibleConversationalModelAdapter(
                        builder,
                        objectMapper,
                        new ConversationalPromptFactory(objectMapper, "safe system prompt"),
                        descriptor(),
                        API_KEY_SENTINEL,
                        1200,
                        publisher);
        LocalRetrievalCoordinator retrieval = new LocalRetrievalCoordinator(
                new RetrievalQueryNormalizer(),
                new KeywordRetriever(),
                new VectorRetriever(),
                new ReciprocalRankFusion(),
                new RetrievalContextValidator(),
                query -> {
                    assertThat(query).isEqualTo(RETRIEVAL_QUERY_SENTINEL);
                    throw new LocalEmbeddingFailureException("LOCAL_INFERENCE_FAILED");
                },
                publisher);
        AnswerKnowledge project = project();
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-27.1",
                "sha256:safe-runtime",
                List.of(project),
                corpus());
        PortfolioGroundingAssembler groundingAssembler =
                new PortfolioGroundingAssembler(6, 12, 12000);
        ConversationToolService toolService = new ConversationToolService(
                adapter,
                (runtimeContent, call) -> {
                    retrieval.retrieve(
                            RETRIEVAL_QUERY_SENTINEL,
                            "sql-audit",
                            AnswerSubjectType.PROJECT,
                            corpus(),
                            claims(),
                            evidence(),
                            RetrievalMode.HYBRID_ENABLED,
                            RetrievalPolicy.firstRelease());
                    return new PublicToolResult(
                            call.getKind(),
                            runtimeContent.getContentVersion(),
                            runtimeContent.getRuntimeBundleHash(),
                            PublicToolResultStatus.SUCCESS,
                            List.of(project),
                            claims(),
                            evidence(),
                            List.of(),
                            List.of());
                },
                1,
                1,
                publisher);
        ConversationalAgentRuntime runtime = new ConversationalAgentRuntime(
                () -> content,
                new ConversationWindowManager(adapter, 12000, 6),
                new ConversationIntentRouter(adapter, 0.65, publisher),
                groundingAssembler,
                toolService,
                adapter,
                new ConversationDraftValidator(adapter),
                new DynamicQuestionService(adapter, groundingAssembler, 3),
                new DeterministicConversationFallback(),
                new ConversationProviderAccess(true),
                new ConversationSubjectGuard(),
                new LoggingConversationDecisionPublisher(publisher),
                publisher);
        return new RuntimeSeams(runtime, server);
    }

    private ModelProviderDescriptor descriptor() {
        return new ModelProviderDescriptor(
                ModelProviderKind.DEEPSEEK_V4_FLASH,
                "conversation-v1",
                URI.create("https://provider.example/v1/chat/completions"),
                "safe-model",
                Set.of("model-policy.v1"),
                Set.of("conversation-answer.v2"),
                Set.of(
                        ModelProviderCapability.STRUCTURED_JSON_OUTPUT,
                        ModelProviderCapability.THINKING_CONTROL,
                        ModelProviderCapability.NON_STREAMING));
    }

    private String requestJson() {
        return """
                {
                  "turnId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
                  "requestToken": "63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "question": "%s",
                  "messages": [
                    {"role": "USER", "content": "%s"},
                    {"role": "ASSISTANT", "content": "safe prior response"}
                  ],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE",
                    "projectSlug": "sql-audit"
                  }
                }
                """.formatted(QUESTION_SENTINEL, HISTORY_SENTINEL);
    }

    private String providerResponse(String content) {
        String escaped = content.strip()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "");
        return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
    }

    private String renderCaptured(
            List<DiagnosticEvent> events,
            List<ILoggingEvent> loggingEvents
    ) {
        StringBuilder rendered = new StringBuilder();
        for (DiagnosticEvent event : events) {
            rendered.append(event.getName()).append(event.getFields());
        }
        PatternLayout layout = new PatternLayout();
        layout.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        layout.setPattern(
                "%date %-5level [%thread] %logger - %msg %kvp %mdc%n%ex");
        layout.start();
        try {
            for (ILoggingEvent loggingEvent : loggingEvents) {
                rendered.append(layout.doLayout(loggingEvent));
            }
        } finally {
            layout.stop();
        }
        return rendered.toString();
    }

    private final class RuntimeSeams {

        private final ConversationalAgentRuntime runtime;
        private final MockRestServiceServer server;

        private RuntimeSeams(
                ConversationalAgentRuntime runtime,
                MockRestServiceServer server
        ) {
            this.runtime = runtime;
            this.server = server;
        }

        private ConversationalAgentRuntime runtime() {
            return runtime;
        }

        private void verify() {
            server.verify();
        }
    }

    private AnswerKnowledge project() {
        return new AnswerKnowledge(
                "sql-audit",
                "Safe project",
                "Safe summary",
                "Safe background",
                List.of("Safe responsibility"),
                "Safe solution",
                List.of("Safe decision"),
                List.of("Safe verification"),
                "Safe outcome",
                "Safe handoff",
                "DELIVERED",
                List.of(),
                evidence(),
                claims());
    }

    private AnswerRetrievalCorpus corpus() {
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                1,
                1.0,
                List.of(new AnswerKeywordIndex.DocumentEntry(
                        "chunk-1", 1, Map.of("retrieval", 1))),
                Map.of("retrieval", 1));
        return new AnswerRetrievalCorpus(
                keywordIndex,
                Map.of("chunk-1", new float[]{1.0f, 0.0f}),
                Map.of("chunk-1", new AnswerRetrievalChunk(
                        "chunk-1",
                        List.of("sql-audit"),
                        List.of("claim-1"),
                        List.of("DELIVERY"),
                        100)));
    }

    private List<AnswerClaimProjection> claims() {
        return List.of(new AnswerClaimProjection(
                "claim-1",
                AnswerClaimCategory.OUTCOME,
                "Safe title",
                "Safe summary",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("DELIVERY"),
                List.of("evidence-1")));
    }

    private List<AnswerEvidence> evidence() {
        return List.of(new AnswerEvidence(
                "evidence-1",
                "Safe evidence",
                "DOCUMENT",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-20"),
                1,
                "Safe summary",
                "APPROVED",
                false));
    }
}
