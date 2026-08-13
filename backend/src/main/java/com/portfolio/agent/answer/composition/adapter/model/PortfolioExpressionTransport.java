package com.portfolio.agent.answer.composition.adapter.model;

import java.time.Duration;

/** Transport seam; an implementation is supplied only by an explicitly enabled deployment. */
public interface PortfolioExpressionTransport {
    String post(String endpoint, String apiKey, Object requestBody, Duration timeout);
}
