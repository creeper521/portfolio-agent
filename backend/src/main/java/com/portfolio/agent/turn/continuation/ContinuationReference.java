package com.portfolio.agent.turn.continuation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/** Closed backend-owned continuation payload forwarded verbatim by clients. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ContinuationReference {
    private final Operation operation;
    private final String contextHandle;
    private final String resultItemId;
    private final String text;
    private final Subject subject;

    private ContinuationReference(
            Operation operation,
            String contextHandle,
            String resultItemId,
            String text,
            Subject subject) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.contextHandle = contextHandle;
        this.resultItemId = resultItemId;
        this.text = text;
        this.subject = subject;
        validate();
    }

    public static ContinuationReference enterResult(
            String contextHandle, String resultItemId) {
        return new ContinuationReference(
                Operation.ENTER_RESULT,
                value(contextHandle, "contextHandle"),
                value(resultItemId, "resultItemId"),
                null, null);
    }

    public static ContinuationReference routeInContext(
            String contextHandle) {
        return new ContinuationReference(
                Operation.ROUTE_IN_CONTEXT,
                value(contextHandle, "contextHandle"),
                null, null, null);
    }

    public static ContinuationReference exitContext(
            String contextHandle) {
        return new ContinuationReference(
                Operation.EXIT_CONTEXT,
                value(contextHandle, "contextHandle"),
                null, null, null);
    }

    public static ContinuationReference reenterSubject(
            String projectId) {
        return new ContinuationReference(
                Operation.REENTER_SUBJECT,
                null, null, null,
                new Subject(SubjectKind.PROJECT, projectId));
    }

    public Operation getOperation() { return operation; }
    public String getContextHandle() { return contextHandle; }
    public String getResultItemId() { return resultItemId; }
    public String getText() { return text; }
    public Subject getSubject() { return subject; }

    private void validate() {
        boolean valid = switch (operation) {
            case ENTER_RESULT -> contextHandle != null
                    && resultItemId != null && text == null && subject == null;
            case ROUTE_IN_CONTEXT, EXIT_CONTEXT -> contextHandle != null
                    && resultItemId == null && text == null && subject == null;
            case REENTER_SUBJECT -> contextHandle == null
                    && resultItemId == null && text == null && subject != null;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "continuation reference shape is invalid");
        }
    }

    private static String value(String value, String name) {
        return ContinuationContext.text(value, name);
    }

    public static final class Subject {
        private final SubjectKind kind;
        private final String reference;
        public Subject(SubjectKind kind, String reference) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.reference = value(reference, "reference");
        }
        public SubjectKind getKind() { return kind; }
        public String getReference() { return reference; }
    }

    public enum Operation {
        ENTER_RESULT,
        ROUTE_IN_CONTEXT,
        EXIT_CONTEXT,
        REENTER_SUBJECT
    }
    public enum SubjectKind { PROJECT }
}
