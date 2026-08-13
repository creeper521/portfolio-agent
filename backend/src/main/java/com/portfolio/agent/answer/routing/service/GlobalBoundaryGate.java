package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Stops requests that exceed the public-data boundary before any planning work. */
public final class GlobalBoundaryGate {

    private static final List<String> BOUNDARY_MARKERS = List.of(
            "private knowledge base", "private repository", "credentials", "access token",
            "internal password", "internal token", "api key", "bypass access control",
            "私有知识库", "私有仓库", "访问凭证", "内部密码", "内部 token", "绕过访问控制");

    public BoundaryDecision evaluate(SemanticTurnInput input) {
        Objects.requireNonNull(input, "input");
        String question = input.getQuestion();
        if (question == null) {
            return BoundaryDecision.allowed();
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        for (String marker : BOUNDARY_MARKERS) {
            if (normalized.contains(marker)) {
                return BoundaryDecision.boundary("ROUTING_GLOBAL_BOUNDARY");
            }
        }
        return BoundaryDecision.allowed();
    }

    static final class BoundaryDecision {

        private final boolean boundary;
        private final List<String> reasonCodes;

        private BoundaryDecision(boolean boundary, List<String> reasonCodes) {
            this.boundary = boundary;
            this.reasonCodes = List.copyOf(reasonCodes);
        }

        static BoundaryDecision allowed() {
            return new BoundaryDecision(false, List.of());
        }

        static BoundaryDecision boundary(String reasonCode) {
            return new BoundaryDecision(true, List.of(reasonCode));
        }

        boolean isBoundary() {
            return boundary;
        }

        List<String> getReasonCodes() {
            return reasonCodes;
        }
    }
}
