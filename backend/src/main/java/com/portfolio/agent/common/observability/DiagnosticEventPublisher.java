package com.portfolio.agent.common.observability;

public interface DiagnosticEventPublisher {

    void publish(DiagnosticEvent event);
}
