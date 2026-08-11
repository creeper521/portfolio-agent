package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One stateless, public-safe answer to a previously rendered clarification field. */
public final class ClarificationResolutionRequest {

    @NotBlank(message = "clarificationId is required")
    @Pattern(regexp = "clarify-[a-f0-9]{32}", message = "clarificationId format is invalid")
    private final String clarificationId;

    @NotBlank(message = "promptCode is required")
    @Pattern(regexp = "[A-Z]+_[A-Z0-9_]+", message = "promptCode format is invalid")
    private final String promptCode;

    @NotBlank(message = "fieldKey is required")
    @Pattern(regexp = "comparisonSubject|subject|taskSplit", message = "fieldKey is invalid")
    private final String fieldKey;

    @Valid
    private final SelectedOptionRequest selectedOption;

    @Size(max = 2000, message = "textValue must not exceed 2000 characters")
    private final String textValue;

    @JsonCreator
    public ClarificationResolutionRequest(
            @JsonProperty("clarificationId") String clarificationId,
            @JsonProperty("promptCode") String promptCode,
            @JsonProperty("fieldKey") String fieldKey,
            @JsonProperty("selectedOption") SelectedOptionRequest selectedOption,
            @JsonProperty("textValue") String textValue) {
        this.clarificationId = clarificationId;
        this.promptCode = promptCode;
        this.fieldKey = fieldKey;
        this.selectedOption = selectedOption;
        this.textValue = textValue;
    }

    public String getClarificationId() { return clarificationId; }
    public String getPromptCode() { return promptCode; }
    public String getFieldKey() { return fieldKey; }
    public SelectedOptionRequest getSelectedOption() { return selectedOption; }
    public String getTextValue() { return textValue; }

    @AssertTrue(message = "exactly one clarification value is required")
    public boolean isValueShapeValid() {
        return (selectedOption != null) != hasText(textValue);
    }

    @AssertTrue(message = "clarification field and value shape are incompatible")
    public boolean isFieldShapeValid() {
        if ("taskSplit".equals(fieldKey)) {
            return selectedOption == null && hasText(textValue);
        }
        return ("comparisonSubject".equals(fieldKey) || "subject".equals(fieldKey))
                && selectedOption != null && !hasText(textValue);
    }

    @Override
    public String toString() {
        return "ClarificationResolutionRequest{hasClarificationId=" + hasText(clarificationId)
                + ", promptCode=" + promptCode + ", fieldKey=" + fieldKey
                + ", hasSelectedOption=" + (selectedOption != null)
                + ", textValue=<redacted>}";
    }

    public static final class SelectedOptionRequest {

        @NotBlank(message = "selectedOption.value is required")
        @Size(max = 100, message = "selectedOption.value must not exceed 100 characters")
        private final String value;

        @Valid
        private final SemanticContextRequest.SubjectReferenceRequest subjectReference;

        @JsonCreator
        public SelectedOptionRequest(
                @JsonProperty("value") String value,
                @JsonProperty("subjectReference")
                SemanticContextRequest.SubjectReferenceRequest subjectReference) {
            this.value = value;
            this.subjectReference = subjectReference;
        }

        public String getValue() { return value; }
        public SemanticContextRequest.SubjectReferenceRequest getSubjectReference() {
            return subjectReference;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
