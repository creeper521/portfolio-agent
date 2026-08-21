package com.portfolio.agent.common.web;

import com.portfolio.agent.common.observability.FrontendDiagnosticProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrontendDiagnosticsBodyLimitFilterTest {

    @Test
    void earlyDisabledAndOversizedResponsesRemainRequestCorrelated() throws Exception {
        List<com.portfolio.agent.common.observability.DiagnosticEvent> events =
                new ArrayList<>();
        RequestDiagnosticsFilter requestFilter = new RequestDiagnosticsFilter(events::add);

        FrontendDiagnosticProperties disabled = new FrontendDiagnosticProperties();
        assertEarlyResponseIsCorrelated(
                requestFilter,
                new FrontendDiagnosticsBodyLimitFilter(disabled),
                "{}",
                404,
                events);

        FrontendDiagnosticProperties enabled = enabledProperties(4);
        assertEarlyResponseIsCorrelated(
                requestFilter,
                new FrontendDiagnosticsBodyLimitFilter(enabled),
                "12345",
                413,
                events);
    }

    @Test
    void rejectsKnownOversizedContentLengthBeforeCallingTheChain() throws Exception {
        FrontendDiagnosticsBodyLimitFilter filter = filterWithLimit(16);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/client-diagnostics");
        request.setContent("0123456789abcdefg".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void rejectsUnknownLengthJsonWithOversizedTrailingWhitespaceBeforeJackson() throws Exception {
        FrontendDiagnosticProperties properties = enabledProperties(16_384);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DiagnosticBodyController())
                .addFilters(unknownLengthFilter(), new FrontendDiagnosticsBodyLimitFilter(properties))
                .build();
        String content = "{\"events\":[]}" + " ".repeat(16_385);

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void allowsBodyAtTheConfiguredLimit() throws Exception {
        FrontendDiagnosticsBodyLimitFilter filter = filterWithLimit(16);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/client-diagnostics");
        request.setContent("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            while (servletRequest.getInputStream().read() != -1) {
                // Drain the request.
            }
            ((MockHttpServletResponse) servletResponse).setStatus(202);
        });

        assertThat(response.getStatus()).isEqualTo(202);
    }

    @Test
    void doesNotLimitOtherRoutes() throws Exception {
        FrontendDiagnosticsBodyLimitFilter filter = filterWithLimit(16);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/agent/turns");
        request.setContent("0123456789abcdefg".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(204);
    }

    private FrontendDiagnosticsBodyLimitFilter filterWithLimit(int maxBodyBytes) {
        return new FrontendDiagnosticsBodyLimitFilter(enabledProperties(maxBodyBytes));
    }

    private void assertEarlyResponseIsCorrelated(
            RequestDiagnosticsFilter requestFilter,
            FrontendDiagnosticsBodyLimitFilter bodyLimitFilter,
            String body,
            int expectedStatus,
            List<com.portfolio.agent.common.observability.DiagnosticEvent> events
    ) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/client-diagnostics");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        int previousEventCount = events.size();

        requestFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                bodyLimitFilter.doFilter(servletRequest, servletResponse,
                        (ignoredRequest, ignoredResponse) -> {
                            throw new AssertionError("early response must not parse the body");
                        }));

        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
        assertThat(events.subList(previousEventCount, events.size()))
                .extracting(com.portfolio.agent.common.observability.DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.rejected");
    }

    private FrontendDiagnosticProperties enabledProperties(int maxBodyBytes) {
        FrontendDiagnosticProperties properties = new FrontendDiagnosticProperties();
        properties.setFrontendIngestEnabled(true);
        properties.setFrontendMaxBodyBytes(maxBodyBytes);
        return properties;
    }

    private Filter unknownLengthFilter() {
        return (ServletRequest request, ServletResponse response, FilterChain chain) ->
                chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                    @Override
                    public int getContentLength() {
                        return -1;
                    }

                    @Override
                    public long getContentLengthLong() {
                        return -1L;
                    }
                }, response);
    }

    @RestController
    private static final class DiagnosticBodyController {

        @PostMapping("/api/client-diagnostics")
        ResponseEntity<Void> ingest(@RequestBody Map<String, Object> body) throws IOException {
            return ResponseEntity.accepted().build();
        }
    }
}
