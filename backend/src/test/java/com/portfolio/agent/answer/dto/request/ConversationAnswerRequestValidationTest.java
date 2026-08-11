package com.portfolio.agent.answer.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import com.portfolio.agent.answer.mapper.SemanticTurnRequestMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void confirmPlanAllowsNoQuestionOnlyWhenItCarriesAnOpaqueConfirmationEnvelope() {
        ConversationAnswerRequest valid = new ConversationAnswerRequest(
                "turn-confirm", UUID.randomUUID(), null, "pcv1-0123456789abcdef",
                ConversationAnswerRequest.TurnAction.CONFIRM_PLAN, null, List.of(), null,
                null, new PlanConfirmationRequest(
                        "confirm-1", "opaque-envelope", "sha256:value", "opaque-token"),
                null, "stp-v1");
        ConversationAnswerRequest missingEnvelope = new ConversationAnswerRequest(
                "turn-confirm", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.CONFIRM_PLAN, null, List.of(), null,
                null, null, null, "stp-v1");

        assertThat(messages(validator.validate(valid))).isEmpty();
        assertThat(messages(validator.validate(missingEnvelope)))
                .contains("question or plan confirmation is invalid for action");
    }

    @Test
    void askAndRegenerateRequireQuestionAndRejectConfirmationEnvelope() {
        ConversationAnswerRequest askWithoutQuestion = new ConversationAnswerRequest(
                "turn-ask", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.ASK, " ", List.of(), null,
                null, null, null, "stp-v1");
        ConversationAnswerRequest regenerateWithEnvelope = new ConversationAnswerRequest(
                "turn-regenerate", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.REGENERATE_PLAN, "regenerate this", List.of(), null,
                null, new PlanConfirmationRequest(
                        "confirm-1", "opaque-envelope", "sha256:value", "opaque-token"),
                new InvalidatedPlanReferenceRequest("plan-1", "sha256:value"), "stp-v1");

        assertThat(messages(validator.validate(askWithoutQuestion)))
                .contains("question or plan confirmation is invalid for action");
        assertThat(messages(validator.validate(regenerateWithEnvelope)))
                .contains("question or plan confirmation is invalid for action");
    }

    @Test
    void regenerateRequiresStructuredSemanticContextAndInvalidatedPlanReference() {
        ConversationAnswerRequest missingContext = new ConversationAnswerRequest(
                "turn-regenerate", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.REGENERATE_PLAN, "regenerate this", List.of(), null,
                null, null, new InvalidatedPlanReferenceRequest("plan-1", "sha256:value"), "stp-v1");
        ConversationAnswerRequest missingReference = new ConversationAnswerRequest(
                "turn-regenerate", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.REGENERATE_PLAN, "regenerate this", List.of(), null,
                new SemanticContextRequest(List.of(), List.of(), "INTERVIEWER"), null, null, "stp-v1");

        assertThat(messages(validator.validate(missingContext)))
                .contains("question or plan confirmation is invalid for action");
        assertThat(messages(validator.validate(missingReference)))
                .contains("question or plan confirmation is invalid for action");
    }

    @Test
    void confirmRejectsAnInvalidatedPlanReference() {
        ConversationAnswerRequest invalid = new ConversationAnswerRequest(
                "turn-confirm", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.CONFIRM_PLAN, null, List.of(), null,
                null, new PlanConfirmationRequest(
                        "confirm-1", "opaque-envelope", "sha256:value", "opaque-token"),
                new InvalidatedPlanReferenceRequest("plan-1", "sha256:value"), "stp-v1");

        assertThat(messages(validator.validate(invalid)))
                .contains("question or plan confirmation is invalid for action");
    }

    @Test
    void keepsPresetAndAgentTurnContractVersionValidationIndependent() {
        ConversationAnswerRequest valid = new ConversationAnswerRequest(
                "turn-ask", UUID.randomUUID(), "question-preset", "pcv1-0123456789abcdef",
                ConversationAnswerRequest.TurnAction.ASK, "safe question", List.of(), null,
                null, null, null, "stp-v1");
        ConversationAnswerRequest invalidAgentTurnContract = new ConversationAnswerRequest(
                "turn-ask", UUID.randomUUID(), "question-preset", "pcv1-0123456789abcdef",
                ConversationAnswerRequest.TurnAction.ASK, "safe question", List.of(), null,
                null, null, null, "pcv1-0123456789abcdef");

        assertThat(messages(validator.validate(valid))).isEmpty();
        assertThat(messages(validator.validate(invalidAgentTurnContract)))
                .contains("agentTurnContract must be stp-v1 when present");
    }

    @Test
    void mapsTheActionAndStructuredSubjectContextWithoutReusingPresetContractVersion() {
        ConversationAnswerRequest request = new ConversationAnswerRequest(
                "turn-ask", UUID.randomUUID(), "question-preset", "pcv1-0123456789abcdef",
                ConversationAnswerRequest.TurnAction.ASK, "safe question", List.of(), null,
                new SemanticContextRequest(List.of(
                        new SemanticContextRequest.SubjectReferenceRequest("PROJECT", "project-a")),
                        List.of(), "INTERVIEWER"), null, null, "stp-v1");

        com.portfolio.agent.answer.routing.domain.SemanticTurnInput input =
                new SemanticTurnRequestMapper().toInput(request, "public-1");

        assertThat(input.getAction()).isEqualTo(
                com.portfolio.agent.answer.routing.domain.SemanticTurnInput.Action.ASK);
        assertThat(input.getAgentTurnContract()).isEqualTo("stp-v1");
        assertThat(input.getPresetContractVersion()).isEqualTo("pcv1-0123456789abcdef");
        assertThat(input.getSemanticContext().getActiveSubjects()).singleElement()
                .extracting(com.portfolio.agent.answer.routing.domain.SubjectReference::getSubjectId)
                .isEqualTo("project-a");
    }

    private static Set<String> messages(Set<ConstraintViolation<ConversationAnswerRequest>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
