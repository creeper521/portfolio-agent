package com.portfolio.agent.common.web;

import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DroppedDiagnosticCounter;
import com.portfolio.agent.common.observability.Slf4jDiagnosticEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class RequestDiagnosticsConfiguration {

    @Bean
    DroppedDiagnosticCounter droppedDiagnosticCounter() {
        return new DroppedDiagnosticCounter();
    }

    @Bean
    DiagnosticEventPublisher diagnosticEventPublisher(DroppedDiagnosticCounter droppedDiagnosticCounter) {
        Logger logger = LoggerFactory.getLogger("com.portfolio.agent.diagnostics");
        return new Slf4jDiagnosticEventPublisher(logger, droppedDiagnosticCounter);
    }

    @Bean
    FilterRegistrationBean<RequestDiagnosticsFilter> requestDiagnosticsFilter(
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        FilterRegistrationBean<RequestDiagnosticsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestDiagnosticsFilter(diagnosticEventPublisher));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
