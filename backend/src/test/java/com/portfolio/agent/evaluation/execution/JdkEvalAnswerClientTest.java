package com.portfolio.agent.evaluation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JdkEvalAnswerClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void answered200ExtractsIdsAndShapeWithoutRetainingBody() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        server = startServer(exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            respond(exchange, 200, """
                    {
                      "turnId": "case-1",
                      "contentVersion": "2026-08-06.1",
                      "resolution": "ANSWERED",
                      "answerScope": "PORTFOLIO",
                      "intentSource": "RULE",
                      "evidenceState": "VERIFIED",
                      "degraded": false,
                      "blocks": [
                        {"content": "回答正文", "claimIds": ["claim-1"], "evidenceIds": ["E-01"], "sourceScope": "PORTFOLIO"},
                        {"content": "回答正文", "claimIds": ["claim-1"], "evidenceIds": ["E-01"], "sourceScope": "PORTFOLIO"}
                      ]
                    }
                    """);
        });

        EvalHttpResult result = new JdkEvalAnswerClient(new ObjectMapper()).answer(
                new EvalHttpRequest(baseUrl(), "case-1", "case-1", "介绍 SQL 审计项目"));

        assertThat(requestPath.get()).isEqualTo("/api/v2/answers");
        assertThat(result.getFailureCode()).isEqualTo(EvalHttpResult.FailureCode.NONE);
        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getClaimIds()).containsExactly("claim-1", "claim-1");
        assertThat(result.getEvidenceIds()).containsExactly("E-01", "E-01");
        assertThat(result.getAnswerShape().getRepeatedContentCount()).isEqualTo(1);
        assertThat(result.getAnswerShape().getRepeatedClaimReferenceCount()).isEqualTo(1);
    }

    @Test
    void httpErrorsMapToClosedFailureCodes() throws Exception {
        server = startServer(exchange ->
                respond(exchange, 429, """
                        {"code":"ANSWER_RATE_LIMITED","noticeCode":"PROVIDER_AUTH_FAILED"}
                        """));

        EvalHttpResult result = new JdkEvalAnswerClient(new ObjectMapper()).answer(
                new EvalHttpRequest(baseUrl(), "case-2", "case-2", "问题"));

        assertThat(result.getFailureCode()).isEqualTo(EvalHttpResult.FailureCode.HTTP_ERROR);
        assertThat(result.getStatusCode()).isEqualTo(429);
        assertThat(result.getNoticeCode()).isEqualTo("PROVIDER_AUTH_FAILED");
    }

    @Test
    void invalidJsonMapsToInvalidJsonFailure() throws Exception {
        server = startServer(exchange -> respond(exchange, 200, "{not valid"));

        EvalHttpResult result = new JdkEvalAnswerClient(new ObjectMapper()).answer(
                new EvalHttpRequest(baseUrl(), "case-3", "case-3", "问题"));

        assertThat(result.getFailureCode()).isEqualTo(EvalHttpResult.FailureCode.INVALID_JSON);
    }

    @Test
    void unknownEnumsAndMissingFieldsDegradeToClosedDefaults() throws Exception {
        server = startServer(exchange -> respond(exchange, 200, """
                {"resolution":"UNKNOWN_RESOLUTION","answerScope":"UNKNOWN_SCOPE"}
                """));

        EvalHttpResult result = new JdkEvalAnswerClient(new ObjectMapper()).answer(
                new EvalHttpRequest(baseUrl(), "case-4", "case-4", "问题"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.NOT_SUPPORTED);
        assertThat(result.getAnswerScope()).isEqualTo(
                com.portfolio.agent.answer.domain.ConversationAnswerScope.PORTFOLIO);
    }

    @Test
    void extractsOnlySemanticTurnCountersFromStpV1Responses() throws Exception {
        server = startServer(exchange -> respond(exchange, 200, """
                {
                  "resolution":"ANSWERED",
                  "answerScope":"PORTFOLIO",
                  "agentTurn": {
                    "disposition":"PARTIAL_READY",
                    "plan":{"taskCount":2,"tasks":[
                      {"goalLabel":"project-a","sourceDomain":"PORTFOLIO"},
                      {"goalLabel":"compare","sourceDomain":"GENERAL",
                       "dependencySummary":"requires 01"}
                    ]},
                    "outcome":{"planOutcome":"PARTIAL","taskSummary":{
                      "totalCount":2,"answeredCount":1,"blockedCount":1,
                      "failedCount":0,"degradedCount":0,
                      "items":[
                        {"goalLabel":"project-a","sourceDomain":"PORTFOLIO"},
                        {"goalLabel":"compare","sourceDomain":"GENERAL"}
                      ]
                    }}
                  }
                }
                """));

        EvalHttpResult result = new JdkEvalAnswerClient(new ObjectMapper()).answer(
                new EvalHttpRequest(baseUrl(), "case-7", "case-7", "question"));

        assertThat(result.getSemanticTurnShape().getTaskCount()).isEqualTo(2);
        assertThat(result.getSemanticTurnShape().getDependencyCount()).isEqualTo(1);
        assertThat(result.getSemanticTurnShape().getAnsweredCount()).isEqualTo(1);
        assertThat(result.getSemanticTurnShape().getBlockedCount()).isEqualTo(1);
        assertThat(result.getSemanticTurnShape().toString()).doesNotContain("project-a");
    }

    @Test
    void localAbsolutePathsAndPrivateKeywordsTriggerPolicyLeak() throws Exception {
        server = startServer(exchange -> respond(exchange, 200, """
                {"resolution":"ANSWERED","blocks":[{"content":"路径 C:\\Users\\me\\secret.txt"}]}
                """));

        EvalHttpResult result = new JdkEvalAnswerClient(new ObjectMapper()).answer(
                new EvalHttpRequest(baseUrl(), "case-5", "case-5", "问题"));

        assertThat(result.getFailureCode()).isEqualTo(EvalHttpResult.FailureCode.POLICY_LEAK);
    }

    @Test
    void transportFailureMapsToClosedTransportFailure() {
        EvalHttpResult result = new JdkEvalAnswerClient(new ObjectMapper()).answer(
                new EvalHttpRequest(
                        "http://127.0.0.1:1", "case-6", "case-6", "问题"));

        assertThat(result.getFailureCode())
                .isEqualTo(EvalHttpResult.FailureCode.TRANSPORT_FAILURE);
        assertThat(result.getDurationMilliseconds()).isGreaterThanOrEqualTo(0L);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private HttpServer startServer(Handler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> handler.handle(exchange));
        httpServer.start();
        return httpServer;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
