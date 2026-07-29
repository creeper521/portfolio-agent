package com.portfolio.agent.common.observability;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

public final class Slf4jDiagnosticEventPublisher implements DiagnosticEventPublisher {

    private final Logger logger;
    private final DroppedDiagnosticCounter droppedDiagnosticCounter;

    public Slf4jDiagnosticEventPublisher(Logger logger, DroppedDiagnosticCounter droppedDiagnosticCounter) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.droppedDiagnosticCounter = Objects.requireNonNull(
                droppedDiagnosticCounter, "dropped diagnostic counter must not be null");
    }

    @Override
    public void publish(DiagnosticEvent event) {
        Objects.requireNonNull(event, "diagnostic event must not be null");
        try {
            LoggingEventBuilder builder = switch (event.getLevel()) {
                case DEBUG -> logger.atDebug();
                case INFO -> logger.atInfo();
                case WARN -> logger.atWarn();
                case ERROR -> logger.atError();
            };
            builder.addKeyValue("event.schema_version", event.getSchemaVersion());
            builder.addKeyValue("event.name", event.getName());
            event.forEachApprovedField(builder::addKeyValue);
            builder.log(event.getName());
        } catch (RuntimeException exception) {
            droppedDiagnosticCounter.increment();
        }
    }
}
