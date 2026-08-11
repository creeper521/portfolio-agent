package com.portfolio.agent.answer.routing.domain;

import java.util.Locale;

public final class SemanticRoutingTypes {

    private SemanticRoutingTypes() {
    }

    public enum SemanticTaskType {
        PORTFOLIO_FACT,
        PORTFOLIO_COMPARE,
        PORTFOLIO_RECOMMEND,
        PORTFOLIO_REFINE_RECOMMENDATION,
        GENERAL_EXPLANATION,
        GENERAL_COMPARISON,
        SYNTHESIS
    }

    public enum TaskSourceDomain {
        PORTFOLIO,
        GENERAL,
        SYNTHESIS
    }

    public enum TaskDependencyType {
        REQUIRES_SUCCESS,
        USES_AVAILABLE_RESULTS,
        ORDER_AFTER
    }

    public enum DependencyOrigin {
        USER_EXPLICIT,
        COMPILER_INFERRED
    }

    public enum RequestedOutput {
        SUMMARY,
        EVIDENCE,
        COMPARISON,
        RECOMMENDATION,
        RISKS,
        NEXT_STEPS,
        DETAILED
    }

    public enum ConfidenceLevel {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum ConfidenceOrigin {
        RULE,
        MODEL_ASSISTED,
        REFERENCE
    }

    public enum ConfidenceField {
        TASK_TYPE,
        SUBJECTS,
        CONSTRAINTS,
        DEPENDENCIES,
        OUTPUT_SCOPE
    }

    public enum SubjectType {
        PROJECT,
        CASE,
        RESULT
    }

    public enum SubjectResolutionSource {
        EXPLICIT_REFERENCE,
        EXPLICIT_TEXT,
        PENDING_PLAN,
        STRUCTURED_RESULT,
        PAGE_CONTEXT,
        ACTIVE_SUBJECT,
        VALIDATED_MODEL_CANDIDATE
    }

    public enum PortfolioFacet {
        OVERVIEW,
        RESPONSIBILITY,
        IMPLEMENTATION,
        DECISION,
        CHALLENGE,
        INCIDENT,
        VERIFICATION,
        LIMITATION,
        LEARNING,
        OUTCOME
    }

    public enum ComparisonDimension {
        ARCHITECTURE,
        IMPLEMENTATION,
        DELIVERY,
        IMPACT,
        RISKS,
        LEARNING
    }

    public enum RoutingAudienceRole {
        INTERVIEWER,
        MENTOR,
        HR,
        GUEST
    }

    public enum CareerTrack {
        BACKEND_ENGINEERING,
        FULL_STACK_ENGINEERING,
        DATA_ENGINEERING
    }

    public enum CapabilityCode {
        JAVA,
        SPRING_BOOT,
        SQL,
        POSTGRESQL,
        VUE,
        TYPESCRIPT,
        TESTING,
        SYSTEM_DESIGN
    }

    public enum RequestedSize {
        TWO(2),
        THREE(3),
        FOUR(4),
        FIVE(5);

        private final int value;

        RequestedSize(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static RequestedSize fromValue(int value) {
            for (RequestedSize requestedSize : values()) {
                if (requestedSize.value == value) {
                    return requestedSize;
                }
            }
            throw new IllegalArgumentException("requestedSize must be between 2 and 5");
        }
    }

    public enum ExplanationDepth {
        BRIEF,
        STANDARD,
        DETAILED
    }

    public enum ExclusionScope {
        PLAN,
        TASK
    }

    public enum ExclusionType {
        SUBJECT,
        OUTPUT,
        DIMENSION,
        CONSTRAINT
    }

    public enum ConstraintCode {
        EXCLUDE_UNVERIFIED,
        EXCLUDE_PERSONAL_DATA,
        EXCLUDE_IMPLEMENTATION_DETAILS,
        EXCLUDE_RECOMMENDATIONS
    }

    static <E extends Enum<E>> E requireEnum(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a supported value", exception);
        }
    }
}
