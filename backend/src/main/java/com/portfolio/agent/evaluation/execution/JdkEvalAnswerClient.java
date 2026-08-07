package com.portfolio.agent.evaluation.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JDK HttpClient answer client without proxy. The response body is parsed in
 * memory, sanitized into ids/counts/closed classification and never retained.
 */
public final class JdkEvalAnswerClient implements EvalAnswerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper mapper;
    private final HttpClient client;

    public JdkEvalAnswerClient(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.client = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public EvalHttpResult answer(EvalHttpRequest request) {
        long startedAt = System.nanoTime();
        try {
            String body = """
                    {
                      "turnId": "%s",
                      "requestToken": "%s",
                      "question": "%s",
                      "messages": [],
                      "context": {"audienceRole": "INTERVIEWER", "source": "AGENT_PAGE"}
                    }
                    """.formatted(request.getTurnId(), UUID.randomUUID(),
                    escape(request.getQuestion()));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(request.getBaseUrl() + "/api/v2/answers"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return parse(request, response, elapsedMillis(startedAt));
        } catch (java.net.http.HttpTimeoutException failure) {
            return failure(startedAt, EvalHttpResult.FailureCode.TIMEOUT);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return failure(startedAt, EvalHttpResult.FailureCode.TRANSPORT_FAILURE);
        } catch (IOException failure) {
            return failure(startedAt, EvalHttpResult.FailureCode.TRANSPORT_FAILURE);
        } catch (RuntimeException failure) {
            return failure(startedAt, EvalHttpResult.FailureCode.CLIENT_ERROR);
        }
    }

    private EvalHttpResult parse(
            EvalHttpRequest request,
            HttpResponse<String> response,
            long durationMilliseconds) {
        int status = response.statusCode();
        String content = response.body();
        if (content == null || content.isBlank()) {
            return new EvalHttpResult(
                    status, AnswerResolution.CAPABILITY_UNAVAILABLE,
                    ConversationAnswerScope.PORTFOLIO, GenerationMode.DETERMINISTIC,
                    AnswerSource.RETRIEVAL, null, null, List.of(), List.of(),
                    false, null, durationMilliseconds,
                    EvalHttpResult.FailureCode.INVALID_JSON, EvalAnswerShape.empty());
        }
        if (containsSensitiveContent(content)) {
            return new EvalHttpResult(
                    status, AnswerResolution.CAPABILITY_UNAVAILABLE,
                    ConversationAnswerScope.PORTFOLIO, GenerationMode.DETERMINISTIC,
                    AnswerSource.RETRIEVAL, null, null, List.of(), List.of(),
                    false, null, durationMilliseconds,
                    EvalHttpResult.FailureCode.POLICY_LEAK, EvalAnswerShape.empty());
        }
        JsonNode root;
        try {
            root = mapper.readTree(content);
        } catch (IOException failure) {
            return new EvalHttpResult(
                    status, AnswerResolution.CAPABILITY_UNAVAILABLE,
                    ConversationAnswerScope.PORTFOLIO, GenerationMode.DETERMINISTIC,
                    AnswerSource.RETRIEVAL, null, null, List.of(), List.of(),
                    false, null, durationMilliseconds,
                    EvalHttpResult.FailureCode.INVALID_JSON, EvalAnswerShape.empty());
        }
        if (status != 200) {
            String notice = text(root, "noticeCode");
            return new EvalHttpResult(
                    status, AnswerResolution.NOT_SUPPORTED,
                    ConversationAnswerScope.PORTFOLIO, GenerationMode.DETERMINISTIC,
                    AnswerSource.RETRIEVAL, null, null, List.of(), List.of(),
                    false, notice, durationMilliseconds,
                    EvalHttpResult.FailureCode.HTTP_ERROR, EvalAnswerShape.empty());
        }
        AnswerResolution resolution = parseResolution(text(root, "resolution"));
        ConversationAnswerScope scope = parseScope(text(root, "answerScope"));
        List<String> claimIds = new ArrayList<>();
        List<String> evidenceIds = new ArrayList<>();
        List<ConversationAnswerBlock> blocks = new ArrayList<>();
        JsonNode blocksNode = root.get("blocks");
        if (blocksNode != null && blocksNode.isArray()) {
            for (JsonNode block : blocksNode) {
                String contentText = text(block, "content");
                List<String> blockClaims = ids(block, "claimIds");
                List<String> blockEvidence = ids(block, "evidenceIds");
                claimIds.addAll(blockClaims);
                evidenceIds.addAll(blockEvidence);
                blocks.add(new ConversationAnswerBlock(
                        parseSourceScope(text(block, "sourceScope")),
                        contentText, blockClaims, blockEvidence));
            }
        }
        boolean degraded = root.path("degraded").asBoolean(false);
        return new EvalHttpResult(
                status, resolution, scope, GenerationMode.DETERMINISTIC,
                AnswerSource.RETRIEVAL, text(root, "intentSource"),
                text(root, "evidenceState"), List.copyOf(claimIds),
                List.copyOf(evidenceIds), degraded, text(root, "noticeCode"),
                durationMilliseconds, EvalHttpResult.FailureCode.NONE,
                EvalAnswerShape.from(blocks));
    }

    private EvalHttpResult failure(long startedAt, EvalHttpResult.FailureCode code) {
        return new EvalHttpResult(
                0, AnswerResolution.CAPABILITY_UNAVAILABLE,
                ConversationAnswerScope.PORTFOLIO, GenerationMode.DETERMINISTIC,
                AnswerSource.RETRIEVAL, null, null, List.of(), List.of(),
                false, null, elapsedMillis(startedAt), code, EvalAnswerShape.empty());
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private List<String> ids(JsonNode node, String field) {
        List<String> ids = new ArrayList<>();
        JsonNode value = node.get(field);
        if (value != null && value.isArray()) {
            for (JsonNode item : value) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    ids.add(item.asText());
                }
            }
        }
        return ids;
    }

    private AnswerResolution parseResolution(String value) {
        if (value == null) {
            return AnswerResolution.NOT_SUPPORTED;
        }
        try {
            return AnswerResolution.valueOf(value);
        } catch (IllegalArgumentException failure) {
            return AnswerResolution.NOT_SUPPORTED;
        }
    }

    private ConversationAnswerScope parseScope(String value) {
        if (value == null) {
            return ConversationAnswerScope.PORTFOLIO;
        }
        try {
            return ConversationAnswerScope.valueOf(value);
        } catch (IllegalArgumentException failure) {
            return ConversationAnswerScope.PORTFOLIO;
        }
    }

    private com.portfolio.agent.answer.domain.ConversationSourceScope parseSourceScope(
            String value) {
        if (value == null) {
            return com.portfolio.agent.answer.domain.ConversationSourceScope.PORTFOLIO;
        }
        try {
            return com.portfolio.agent.answer.domain.ConversationSourceScope.valueOf(value);
        } catch (IllegalArgumentException failure) {
            return com.portfolio.agent.answer.domain.ConversationSourceScope.PORTFOLIO;
        }
    }

    private boolean containsSensitiveContent(String content) {
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        return lower.contains(":\\users")
                || lower.contains(":\\windows")
                || lower.contains("begin private key")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("password")
                || lower.contains("visitor_secret");
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
