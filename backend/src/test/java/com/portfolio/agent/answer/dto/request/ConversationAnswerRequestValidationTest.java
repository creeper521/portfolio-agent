package com.portfolio.agent.answer.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import com.portfolio.agent.answer.mapper.SemanticTurnRequestMapper;
import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void mapsPlanAdjustmentOnlyWhenPendingPlanIdentityMatches() throws Exception {
        ConversationAnswerRequest request = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {
                  "turnId":"turn-adjust",
                  "requestToken":"6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "action":"ASK",
                  "question":"介绍项目并给出推荐",
                  "messages":[],
                  "semanticContext":{
                    "activeSubjects":[],
                    "resultReferences":[],
                    "pendingPlanReference":{"planId":"plan-1","planFingerprint":"sha256:one"}
                  },
                  "planAdjustment":{
                    "instruction":"去掉推荐步骤",
                    "pendingPlanReference":{"planId":"plan-1","planFingerprint":"sha256:one"}
                  },
                  "agentTurnContract":"stp-v1"
                }
                """, ConversationAnswerRequest.class);

        assertThat(messages(validator.validate(request))).isEmpty();
        SemanticTurnInput input = new SemanticTurnRequestMapper().toInput(request, "public-1");
        assertThat(input.getRoutingQuestion()).contains("去掉推荐步骤");
        assertThat(input.getPlanAdjustment().getPendingPlanReference().getPlanFingerprint())
                .isEqualTo("sha256:one");
    }

    @Test
    void rejectsPlanAdjustmentWhenPendingPlanIdentityDoesNotMatchContext() throws Exception {
        ConversationAnswerRequest request = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {
                  "turnId":"turn-adjust",
                  "requestToken":"6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "action":"ASK",
                  "question":"介绍项目",
                  "messages":[],
                  "semanticContext":{"pendingPlanReference":{"planId":"plan-1","planFingerprint":"sha256:one"}},
                  "planAdjustment":{
                    "instruction":"去掉推荐步骤",
                    "pendingPlanReference":{"planId":"plan-1","planFingerprint":"sha256:two"}
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThatThrownBy(() -> new SemanticTurnRequestMapper().toInput(request, "public-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending plan references must match");
    }

    @Test
    void mapsClarificationSelectionToAnExplicitStructuredSubject() throws Exception {
        ConversationAnswerRequest request = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {
                  "turnId":"turn-clarify",
                  "requestToken":"6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "action":"ASK",
                  "question":"比较两个项目",
                  "messages":[],
                  "semanticContext":{},
                  "clarificationResolution":{
                    "clarificationId":"clarify-0123456789abcdef0123456789abcdef",
                    "promptCode":"ROUTING_COMPARISON_SUBJECT_MISSING",
                    "fieldKey":"comparisonSubject",
                    "selectedOption":{
                      "value":"project-b",
                      "subjectReference":{"subjectType":"PROJECT","subjectId":"project-b"}
                    }
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(messages(validator.validate(request))).isEmpty();
        SemanticTurnInput input = new SemanticTurnRequestMapper().toInput(request, "public-1");
        assertThat(input.getExplicitSubjectReferences()).extracting(
                        com.portfolio.agent.answer.routing.domain.SubjectReference::getSubjectId)
                .containsExactly("project-b");
    }

    private static Set<String> messages(Set<ConstraintViolation<ConversationAnswerRequest>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
