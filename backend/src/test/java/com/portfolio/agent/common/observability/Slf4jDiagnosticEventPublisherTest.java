package com.portfolio.agent.common.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Slf4jDiagnosticEventPublisherTest {

    @Test
    void publishesEventMetadataAndFieldsAtTheEventLevel() {
        Logger logger = mock(Logger.class);
        LoggingEventBuilder builder = mock(LoggingEventBuilder.class);
        DroppedDiagnosticCounter counter = new DroppedDiagnosticCounter();
        DiagnosticEvent event = DiagnosticEvent.builder(
                "http.request.completed", DiagnosticLevel.INFO)
                .field("http.status_code", 200)
                .build();
        when(logger.atInfo()).thenReturn(builder);

        new Slf4jDiagnosticEventPublisher(logger, counter).publish(event);

        verify(logger).atInfo();
        verify(builder).addKeyValue("event.schema_version", 1);
        verify(builder).addKeyValue("event.name", "http.request.completed");
        verify(builder).addKeyValue("http.status_code", 200);
        verify(builder).log("http.request.completed");
        assertThat(counter.count()).isZero();
    }

    @Test
    void dropsEventWhenLoggingAdapterThrows() {
        Logger logger = mock(Logger.class);
        LoggingEventBuilder builder = mock(LoggingEventBuilder.class);
        DroppedDiagnosticCounter counter = new DroppedDiagnosticCounter();
        DiagnosticEvent event = DiagnosticEvent.builder(
                "http.request.failed", DiagnosticLevel.ERROR)
                .build();
        when(logger.atError()).thenReturn(builder);
        org.mockito.Mockito.doThrow(new RuntimeException("adapter unavailable"))
                .when(builder).log("http.request.failed");

        new Slf4jDiagnosticEventPublisher(logger, counter).publish(event);

        assertThat(counter.count()).isEqualTo(1);
    }
}
