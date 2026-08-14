package com.portfolio.agent.answer.synthesis.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Product-level gate for deterministic cross-domain relation publication. */
@ConfigurationProperties(prefix = "portfolio.agent.cross-domain-relations")
public final class CrossDomainRelationProperties {
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
