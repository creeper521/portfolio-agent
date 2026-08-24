package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.regex.Pattern;

/** Credential-free, settled projection of the model selection and adopted participation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ModelExecutionProjection {
    private static final Pattern MODEL_REF =
            Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?");

    public enum SelectionKind { MODEL, NONE }
    public enum Participation {
        NONE,
        GOAL_INTERPRETATION_ONLY,
        ANSWER_GENERATION,
        GOAL_AND_ANSWER,
        ATTEMPTED_UNAVAILABLE
    }

    private final SelectionKind selectionKind;
    private final String requestedModelRef;
    private final String selectionVersion;
    private final Participation participation;

    @JsonCreator
    public ModelExecutionProjection(
            @JsonProperty("selectionKind") SelectionKind selectionKind,
            @JsonProperty("requestedModelRef") String requestedModelRef,
            @JsonProperty("selectionVersion") String selectionVersion,
            @JsonProperty("participation") Participation participation) {
        this.selectionKind = Objects.requireNonNull(selectionKind, "selectionKind");
        this.participation = Objects.requireNonNull(participation, "participation");
        if (selectionKind == SelectionKind.NONE) {
            if (requestedModelRef != null || selectionVersion != null) {
                throw new IllegalArgumentException(
                        "NONE model execution cannot carry model fields");
            }
            if (participation != Participation.NONE) {
                throw new IllegalArgumentException(
                        "NONE selection cannot report model participation");
            }
            this.requestedModelRef = null;
            this.selectionVersion = null;
            return;
        }
        if (requestedModelRef == null || !MODEL_REF.matcher(requestedModelRef).matches()) {
            throw new IllegalArgumentException("requestedModelRef is invalid");
        }
        if (selectionVersion == null || selectionVersion.isBlank()
                || selectionVersion.length() > 128) {
            throw new IllegalArgumentException("selectionVersion is invalid");
        }
        this.requestedModelRef = requestedModelRef;
        this.selectionVersion = selectionVersion;
    }

    public static ModelExecutionProjection none() {
        return new ModelExecutionProjection(
                SelectionKind.NONE, null, null, Participation.NONE);
    }

    public static ModelExecutionProjection model(
            String requestedModelRef,
            String selectionVersion,
            Participation participation) {
        return new ModelExecutionProjection(
                SelectionKind.MODEL, requestedModelRef, selectionVersion, participation);
    }

    public SelectionKind getSelectionKind() { return selectionKind; }
    public String getRequestedModelRef() { return requestedModelRef; }
    public String getSelectionVersion() { return selectionVersion; }
    public Participation getParticipation() { return participation; }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ModelExecutionProjection that
                && selectionKind == that.selectionKind
                && Objects.equals(requestedModelRef, that.requestedModelRef)
                && Objects.equals(selectionVersion, that.selectionVersion)
                && participation == that.participation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                selectionKind, requestedModelRef, selectionVersion, participation);
    }
}
