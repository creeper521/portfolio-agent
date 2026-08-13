package com.portfolio.agent.answer.composition.adapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.composition.domain.ModelExpressionDeadline;
import com.portfolio.agent.answer.composition.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionProviderException;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiCompatiblePortfolioExpressionAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void sendsOneStaticJsonCallWithBoundedDeadlineAndNoExtraContent() {
        CapturingTransport transport = new CapturingTransport(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"schemaVersion\\\":\\\"portfolio-expression-draft.v1\\\"}\"}}]}");
        List<DiagnosticEvent> events = new ArrayList<>();
        OpenAiCompatiblePortfolioExpressionAdapter adapter = adapter(transport, events);
        String projectedPublicInput = "{\"schemaVersion\":\"portfolio-expression-input.v1\",\"subjects\":[{\"alias\":\"P01\"}]}";

        com.portfolio.agent.answer.composition.domain.ModelExpressionResult result = adapter.express(
                new ModelExpressionRequest("portfolio-expression-input.v1", projectedPublicInput),
                new ModelExpressionDeadline(NOW.plusSeconds(2)));

        assertThat(result.getResponse()).contains("portfolio-expression-draft.v1");
        assertThat(transport.calls).isEqualTo(1);
        assertThat(transport.timeout).isEqualTo(Duration.ofSeconds(2));
        assertThat(transport.endpoint).isEqualTo("https://example.test/chat/completions");
        assertThat(transport.apiKey).isEqualTo("secret");
        assertThat(transport.body).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) transport.body;
        assertThat(body.get("model")).isEqualTo("deepseek-v4-flash");
        assertThat(body.get("stream")).isEqualTo(false);
        assertThat(body.get("temperature")).isEqualTo(0.1d);
        assertThat(body.get("max_tokens")).isEqualTo(1600);
        assertThat(body.toString()).contains(projectedPublicInput)
                .doesNotContain("questionSpan", "conversationId", "turnId", "goalLabel");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("expression.provider.completed");
            assertThat(event.getFields()).doesNotContainKeys("request", "response", "alias", "apiKey");
        });
    }

    @Test
    void propagatesProviderFailureAfterExactlyOneAttempt() {
        CapturingTransport transport = new CapturingTransport(null);
        transport.failure = new PortfolioExpressionProviderException("safe failure");

        assertThatThrownBy(() -> adapter(transport, new ArrayList<>()).express(
                new ModelExpressionRequest("portfolio-expression-input.v1", "{}"),
                new ModelExpressionDeadline(NOW.plusSeconds(10))))
                .isInstanceOf(PortfolioExpressionProviderException.class);
        assertThat(transport.calls).isEqualTo(1);
    }

    @Test
    void invalidProviderEnvelopeReportsResponsePresentWithoutLoggingBody() {
        CapturingTransport transport = new CapturingTransport("{not-json:SECRET_RESPONSE_SENTINEL}");
        List<DiagnosticEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> adapter(transport, events).express(
                new ModelExpressionRequest("portfolio-expression-input.v1", "{}"),
                new ModelExpressionDeadline(NOW.plusSeconds(2))))
                .isInstanceOf(PortfolioExpressionProviderException.class);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("expression.provider.failed");
            assertThat(event.getFields()).containsEntry("response.present", true);
            assertThat(event.toString()).doesNotContain("SECRET_RESPONSE_SENTINEL");
        });
    }

    private OpenAiCompatiblePortfolioExpressionAdapter adapter(
            CapturingTransport transport, List<DiagnosticEvent> events) {
        return new OpenAiCompatiblePortfolioExpressionAdapter(
                transport, new PortfolioExpressionPromptFactory(), new ObjectMapper(),
                "https://example.test/chat/completions", "secret", "deepseek-v4-flash",
                1600, Duration.ofSeconds(4), Clock.fixed(NOW, ZoneOffset.UTC),
                new PortfolioExpressionDiagnostics(events::add));
    }

    private static final class CapturingTransport implements PortfolioExpressionTransport {
        private final String response;
        private RuntimeException failure;
        private int calls;
        private String endpoint;
        private String apiKey;
        private Object body;
        private Duration timeout;
        private CapturingTransport(String response) { this.response = response; }
        @Override public String post(String endpoint, String apiKey, Object body, Duration timeout) {
            calls++; this.endpoint = endpoint; this.apiKey = apiKey; this.body = body; this.timeout = timeout;
            if (failure != null) throw failure;
            return response;
        }
    }
}
