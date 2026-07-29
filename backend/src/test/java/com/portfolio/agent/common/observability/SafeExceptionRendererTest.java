package com.portfolio.agent.common.observability;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SafeExceptionRendererTest {

    @Test
    void rendersOnlySafeFirstPartyFrameData() {
        RuntimeException exception = new RuntimeException(
                "SECRET_EXCEPTION_MESSAGE",
                new IllegalStateException("SECRET_CAUSE_MESSAGE"));
        exception.addSuppressed(new IllegalArgumentException("SECRET_SUPPRESSED_MESSAGE"));
        exception.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(
                        "com.portfolio.agent.answer.service.SecretService",
                        "execute",
                        "C:\\Users\\private-user\\workspace\\SecretService.java",
                        42),
                new StackTraceElement(
                        "org.springframework.web.servlet.DispatcherServlet",
                        "doDispatch",
                        "DispatcherServlet.java",
                        1000)
        });

        String rendered = new SafeExceptionRenderer().render(exception);

        assertThat(rendered)
                .contains(RuntimeException.class.getName())
                .contains("com.portfolio.agent.answer.service.SecretService")
                .contains("execute")
                .contains("SecretService.java")
                .contains("42")
                .doesNotContain(
                        "SECRET_EXCEPTION_MESSAGE",
                        "SECRET_CAUSE_MESSAGE",
                        "SECRET_SUPPRESSED_MESSAGE",
                        "C:\\Users\\private-user",
                        "org.springframework");
    }

    @Test
    void rendersAtMostTwentyFirstPartyFrames() {
        RuntimeException exception = new RuntimeException("SECRET_EXCEPTION_MESSAGE");
        StackTraceElement[] frames = IntStream.range(0, 25)
                .mapToObj(index -> new StackTraceElement(
                        "com.portfolio.agent.feature.Service" + index,
                        "run" + index,
                        "Service" + index + ".java",
                        index + 1))
                .toArray(StackTraceElement[]::new);
        exception.setStackTrace(frames);

        String rendered = new SafeExceptionRenderer().render(exception);

        assertThat(rendered.lines().filter(line -> line.startsWith("at ")).count())
                .isEqualTo(20);
        assertThat(rendered).contains("Service19.java").doesNotContain("Service20.java");
    }
}
