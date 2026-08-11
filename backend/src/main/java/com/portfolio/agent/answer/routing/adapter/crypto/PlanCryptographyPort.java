package com.portfolio.agent.answer.routing.adapter.crypto;

import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.service.ValidatedSemanticTurnPlan;

import java.util.Objects;

/** Authenticated sealing boundary for stateless plan confirmation. */
public interface PlanCryptographyPort {

    SealedPlan seal(
            ValidatedSemanticTurnPlan plan,
            PlanConfirmation.Identity identity,
            PlanConfirmation.VersionBinding versionBinding);

    boolean isIntegrityValid(PlanConfirmation.Submission submission);

    OpenedPlan open(PlanConfirmation.Submission submission);

    final class SealedPlan {

        private final String confirmationPlan;
        private final String integrityToken;

        public SealedPlan(String confirmationPlan, String integrityToken) {
            this.confirmationPlan = requireText(confirmationPlan, "confirmationPlan");
            this.integrityToken = requireText(integrityToken, "integrityToken");
        }

        public String getConfirmationPlan() {
            return confirmationPlan;
        }

        public String getIntegrityToken() {
            return integrityToken;
        }

        @Override
        public String toString() {
            return "SealedPlan{hasOpaqueEnvelope=true, hasIntegrityToken=true}";
        }
    }

    final class OpenedPlan {

        private final SemanticTurnPlan plan;
        private final PlanConfirmation.Identity identity;
        private final PlanConfirmation.VersionBinding versionBinding;

        public OpenedPlan(
                SemanticTurnPlan plan,
                PlanConfirmation.Identity identity,
                PlanConfirmation.VersionBinding versionBinding) {
            this.plan = Objects.requireNonNull(plan, "plan");
            this.identity = Objects.requireNonNull(identity, "identity");
            this.versionBinding = Objects.requireNonNull(versionBinding, "versionBinding");
        }

        public SemanticTurnPlan getPlan() {
            return plan;
        }

        public PlanConfirmation.Identity getIdentity() {
            return identity;
        }

        public PlanConfirmation.VersionBinding getVersionBinding() {
            return versionBinding;
        }

        @Override
        public String toString() {
            return "OpenedPlan{taskCount=" + plan.getTasks().size() + ", hasIdentity=true, hasVersionBinding=true}";
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
