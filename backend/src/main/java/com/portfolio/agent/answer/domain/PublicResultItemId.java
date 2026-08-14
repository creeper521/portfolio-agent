package com.portfolio.agent.answer.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable public identity shared by response projection and persisted continuation Context. */
public final class PublicResultItemId {
    private PublicResultItemId() { }

    public static String forRecommendation(String taskId, String portfolioId) {
        String value = requireText(taskId, "taskId") + "|" + requireText(portfolioId, "portfolioId") + "||";
        return "result-item-" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    public static String recommendationBatch(String taskId) {
        String value = requireText(taskId, "taskId") + "|||";
        return "recommendation-batch-" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
