package com.portfolio.agent.common.observability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "portfolio.diagnostics")
public class FrontendDiagnosticProperties {

    private boolean frontendIngestEnabled;

    @Min(1)
    @Max(10)
    private int frontendMaxBatchSize = 10;

    @Min(1)
    private int frontendMaxBodyBytes = 16_384;

    @Min(1)
    private int frontendEventsPerMinute = 30;

    public boolean isFrontendIngestEnabled() {
        return frontendIngestEnabled;
    }

    public void setFrontendIngestEnabled(boolean frontendIngestEnabled) {
        this.frontendIngestEnabled = frontendIngestEnabled;
    }

    public int getFrontendMaxBatchSize() {
        return frontendMaxBatchSize;
    }

    public void setFrontendMaxBatchSize(int frontendMaxBatchSize) {
        this.frontendMaxBatchSize = frontendMaxBatchSize;
    }

    public int getFrontendMaxBodyBytes() {
        return frontendMaxBodyBytes;
    }

    public void setFrontendMaxBodyBytes(int frontendMaxBodyBytes) {
        this.frontendMaxBodyBytes = frontendMaxBodyBytes;
    }

    public int getFrontendEventsPerMinute() {
        return frontendEventsPerMinute;
    }

    public void setFrontendEventsPerMinute(int frontendEventsPerMinute) {
        this.frontendEventsPerMinute = frontendEventsPerMinute;
    }
}
