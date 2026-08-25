package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * OpenAI 兼容协议的结构化模型传输客户端：按已解析的 {@link ModelTransportBinding}
 * 发起单次 HTTP 调用并解析响应。
 *
 * <p>这是三重准入之后的执行末端：调用方（各 Operation）传入的绑定已经过
 * model-runtime、Provider 准入与 Operation 策略校验，本类只对这一个绑定执行，
 * 不做任何重试、修复或 Provider 回退——失败一律以封闭的
 * {@link StructuredModelFailure} 码上抛，由上层决定终态。
 *
 * <p>关键防护与不变量：
 * <ul>
 *   <li>超时取 Turn 截止时间与 Operation 超时的较小值，任一耗尽都判
 *       DEADLINE_EXCEEDED 并取消未完成的请求；</li>
 *   <li>响应体经限流订阅器（{@link LimitedByteArraySubscriber}）读取，
 *       超过 {@value #MAX_RESPONSE_BYTES} 字节即中止并判 RESPONSE_TOO_LARGE，
 *       避免超大响应耗尽内存；</li>
 *   <li>HTTP 状态码映射为封闭失败码（鉴权、计费、限流、Provider 不可用等），
 *       429 时解析 Retry-After（缺省 {@value #DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS}
 *       秒，并夹取到 1..300 秒）；</li>
 *   <li>响应 JSON 必须是恰好一个 choice 且 content 为非空文本，否则判
 *       RESPONSE_ENVELOPE_INVALID；</li>
 *   <li>每次调用发布仅含操作名、结果与耗时桶的诊断事件，诊断失败不影响模型行为。</li>
 * </ul>
 */
public final class OpenAiCompatibleStructuredModelTransport implements StructuredModelTransport {
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final int DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS = 30;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Duration operationTimeout;
    private final DiagnosticEventPublisher diagnostics;
    private final Function<ModelTransportBinding, URI> endpointResolver;

    /** 公开构造：endpoint 直接取绑定中的 URI；测试构造可注入自定义 endpoint 解析器。 */
    public OpenAiCompatibleStructuredModelTransport(
            HttpClient client, ObjectMapper mapper,
            Duration operationTimeout, DiagnosticEventPublisher diagnostics) {
        this(client, mapper, operationTimeout, diagnostics,
                ModelTransportBinding::getEndpoint);
    }

    /**
     * 全参构造。
     *
     * @param endpointResolver 从绑定解析实际请求 endpoint 的函数，
     *                         生产固定为绑定 endpoint，仅供测试重定向
     */
    OpenAiCompatibleStructuredModelTransport(
            HttpClient client, ObjectMapper mapper,
            Duration operationTimeout,
            DiagnosticEventPublisher diagnostics,
            Function<ModelTransportBinding, URI> endpointResolver) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper");
        this.operationTimeout = java.util.Objects.requireNonNull(
                operationTimeout, "operationTimeout");
        this.diagnostics = java.util.Objects.requireNonNull(diagnostics, "diagnostics");
        this.endpointResolver = java.util.Objects.requireNonNull(
                endpointResolver, "endpointResolver");
    }

    /**
     * 对指定绑定执行一次结构化模型调用（外部 HTTP 调用，可能阻塞至超时）。
     *
     * <p>流程：Turn 截止时间与 Operation 超时取较小值 → 组装
     * OpenAI 兼容请求体（协议画像注入结构化输出字段，max_tokens 取请求
     * 预算与绑定上限的较小值）→ 异步发送并在剩余时间内等待 → 校验状态码、
     * JSON 与响应信封 → 返回唯一 choice 的文本内容。
     *
     * @param binding 已通过准入的传输绑定（含 endpoint 与凭证）
     * @param request 已通过 Operation 策略校验的请求
     * @return content 为非空文本的模型响应
     * @throws StructuredModelFailure 截止时间耗尽、传输不可用、鉴权/计费被拒、
     *         限流（携带夹取后的 Retry-After 秒数）、Provider 不可用/拒绝、
     *         响应过大、JSON 或响应信封不合法等任一封闭失败
     */
    @Override
    public StructuredModelResponse execute(
            ModelTransportBinding binding, StructuredModelRequest request) {
        ModelTransportBinding resolvedBinding = java.util.Objects.requireNonNull(
                binding, "binding");
        long startedAt = System.nanoTime();
        try {
            TurnDeadline operationDeadline =
                    request.deadline().cappedAt(operationTimeout);
            String requestBody = body(resolvedBinding, request);
            long timeout = operationDeadline.remainingMillis();
            if (timeout < 1) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.DEADLINE_EXCEEDED);
            }
            URI endpoint = java.util.Objects.requireNonNull(
                    endpointResolver.apply(resolvedBinding), "resolved endpoint");
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Authorization", resolvedBinding.authorizationHeaderValue())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(
                    httpRequest, limitedByteArrayHandler(MAX_RESPONSE_BYTES));
            HttpResponse<byte[]> response;
            try {
                response = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutFailure) {
                // 等待超时：主动取消底层请求，避免泄漏仍在运行的连接。
                future.cancel(true);
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.DEADLINE_EXCEEDED,
                        timeoutFailure);
            } catch (ExecutionException executionFailure) {
                // 异步阶段失败：按异常链归类，避免把 HTTP 层异常直接外泄。
                Throwable cause = executionFailure.getCause();
                if (containsCause(cause, HttpTimeoutException.class)) {
                    throw new StructuredModelFailure(
                            StructuredModelFailure.Code.DEADLINE_EXCEEDED,
                            cause);
                }
                if (containsCause(cause, ResponseTooLargeException.class)) {
                    throw new StructuredModelFailure(
                            StructuredModelFailure.Code.RESPONSE_TOO_LARGE, cause);
                }
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE,
                        cause);
            } catch (CancellationException cancelled) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE,
                        cancelled);
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE,
                        interrupted);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                StructuredModelFailure.Code code =
                        classifyHttpStatus(response.statusCode());
                Integer retryAfterSeconds = code == StructuredModelFailure.Code.RATE_LIMITED
                        ? retryAfterSeconds(response) : null;
                throw new StructuredModelFailure(code, retryAfterSeconds, null);
            }
            JsonNode root;
            try {
                root = mapper.readTree(response.body());
            } catch (Exception invalidJson) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.RESPONSE_JSON_INVALID, invalidJson);
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() != 1) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID);
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual() || content.textValue().isBlank()) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID);
            }
            StructuredModelResponse result = new StructuredModelResponse(content.textValue());
            publish(request.operation(), true, null, startedAt);
            return result;
        } catch (StructuredModelFailure failure) {
            publish(request.operation(), false, failure.getCode().name(), startedAt);
            throw failure;
        }
        catch (Exception failure) {
            // 兜底：任何未归类的异常都收敛为封闭的 TRANSPORT_UNAVAILABLE，不外泄细节。
            publish(request.operation(), false,
                    StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE.name(), startedAt);
            throw new StructuredModelFailure(StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE, failure);
        }
    }

    /**
     * 发布一次 Provider 调用的诊断事件（操作名、结果、失败码、耗时桶）。
     * 诊断发布失败被静默吞掉：诊断永远不得改变模型调用行为。
     */
    private void publish(String operation, boolean success, String failureCode, long startedAt) {
        try {
            DiagnosticEvent.Builder event = DiagnosticEvent.builder(
                            success ? "provider.call.completed" : "provider.call.failed",
                            success ? DiagnosticLevel.INFO : DiagnosticLevel.WARN)
                    .field("provider.operation", operation)
                    .field("event.outcome", success ? "SUCCESS" : "FAILURE")
                    .field("duration.bucket", durationBucket(startedAt))
                    .field("response.present", success);
            if (failureCode != null) event.field("failure.code", failureCode);
            if (failureCode != null) {
                event.field("failure.layer",
                        StructuredModelFailure.Code.valueOf(failureCode).getLayer());
            }
            diagnostics.publish(event.build());
        } catch (RuntimeException ignored) {
            // Diagnostics never change model behavior.
        }
    }

    /** 把调用耗时折算为粗粒度耗时桶，用于低基数的诊断统计。 */
    private String durationBucket(long startedAt) {
        long millis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (millis < 100) return "LT_100_MS";
        if (millis < 500) return "FROM_100_TO_499_MS";
        if (millis < 2000) return "FROM_500_TO_1999_MS";
        return "GTE_2000_MS";
    }

    /**
     * 组装 OpenAI 兼容请求体：模型名、system/user 双消息、协议画像注入的
     * 结构化输出字段、max_tokens（取请求预算与绑定上限的较小值）与 temperature。
     */
    private String body(
            ModelTransportBinding binding,
            StructuredModelRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", binding.getModelName());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        binding.getProtocolProfile().applyStructuredOutputFields(payload);
        payload.put("max_tokens", Math.min(
                request.maxOutputTokens(), binding.getMaxOutputTokens()));
        payload.put("temperature", request.temperature());
        return mapper.writeValueAsString(payload);
    }

    /** 把非 2xx 状态码映射为封闭失败码（鉴权/计费/限流/Provider 不可用/拒绝）。 */
    private StructuredModelFailure.Code classifyHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return StructuredModelFailure.Code.AUTHENTICATION_REJECTED;
        }
        if (status == 402) {
            return StructuredModelFailure.Code.BILLING_REJECTED;
        }
        if (status == 429) {
            return StructuredModelFailure.Code.RATE_LIMITED;
        }
        if (status >= 500) {
            return StructuredModelFailure.Code.PROVIDER_UNAVAILABLE;
        }
        return StructuredModelFailure.Code.PROVIDER_REJECTED;
    }

    /**
     * 解析限流响应的 Retry-After（秒）：缺失或非法时用缺省值，
     * 合法值夹取到 1..300 秒，防止 Provider 返回异常巨大的等待时间。
     */
    private int retryAfterSeconds(HttpResponse<?> response) {
        String raw = response.headers().firstValue("Retry-After").orElse(null);
        if (raw == null) {
            return DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS;
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            return (int) Math.max(1L, Math.min(300L, seconds));
        } catch (NumberFormatException invalid) {
            return DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS;
        }
    }

    /** 沿异常链查找指定类型的成因；自引用成因（cause == self）时终止防死循环。 */
    private boolean containsCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 构造带字节上限的响应体处理器。 */
    private HttpResponse.BodyHandler<byte[]> limitedByteArrayHandler(int maxBytes) {
        return responseInfo -> new LimitedByteArraySubscriber(maxBytes);
    }

    /**
     * 限流字节订阅器：逐块接收响应体，一旦累计字节数将超过上限就取消订阅并
     * 以 {@link ResponseTooLargeException} 失败，保证不会把超大响应完整读入内存。
     */
    private static final class LimitedByteArraySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream body;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedByteArraySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
            this.body = new ByteArrayOutputStream(Math.min(maxBytes, 8 * 1024));
        }

        @Override public CompletionStage<byte[]> getBody() { return result; }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maxBytes - body.size()) {
                    // 超出预算：立即取消订阅并失败，不再接收剩余数据。
                    subscription.cancel();
                    result.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                body.writeBytes(bytes);
            }
            if (!result.isDone()) {
                subscription.request(1);
            }
        }

        @Override public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override public void onComplete() {
            result.complete(body.toByteArray());
        }
    }

    /** 内部标记异常：仅在订阅器与失败归类之间传递"响应体超限"信号。 */
    private static final class ResponseTooLargeException extends RuntimeException { }
}
