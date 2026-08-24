package com.portfolio.agent.turn.api.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTurnRequestValidationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void decodesEveryClosedCommandVariant() throws Exception {
        assertValid(request("""
                {"kind":"ASK","input":{"kind":"FREE_TEXT","text":"介绍 SQL 审计项目"},
                 "referenceContextHandle":"ctx_reference"}
                """));
        assertValid(request("""
                {"kind":"ASK","input":{"kind":"PRESET","presetId":"question-sql-audit-detail",
                 "presetRevision":"pcv1-0123456789abcdef"}}
                """));
        assertValid(request("""
                {"kind":"CONTINUE","operation":"ENTER_RESULT",
                 "contextHandle":"ctx_opaque","resultItemId":"item_opaque"}
                """));
        assertValid(request("""
                {"kind":"CONTINUE","operation":"ROUTE_IN_CONTEXT",
                 "contextHandle":"ctx_opaque","text":"继续说明这一项"}
                """));
        assertValid(request("""
                {"kind":"CONTINUE","operation":"EXIT_CONTEXT",
                 "contextHandle":"ctx_opaque"}
                """));
        assertValid(request("""
                {"kind":"CONTINUE","operation":"REENTER_SUBJECT",
                 "subject":{"kind":"PROJECT","reference":"sql-audit"}}
                """));
        assertValid(request("""
                {"kind":"RESOLVE_CLARIFICATION","clarificationId":"clarification_opaque",
                 "answer":{"kind":"CHOICE","choiceId":"choice_opaque"}}
                """));
        assertValid(request("""
                {"kind":"RESOLVE_CLARIFICATION","clarificationId":"clarification_opaque",
                 "answer":{"kind":"TEXT","text":"SQL 审计项目"}}
                """));
    }

    @Test
    void acceptsClosedModelAndNoneSelections() throws Exception {
        AgentTurnRequest model = requestWithSelection("""
                {"kind":"MODEL","modelRef":"glm-4-7-flash",
                 "selectionVersion":"glm-4-7-flash-v1"}
                """);
        AgentTurnRequest none = requestWithSelection("""
                {"kind":"NONE"}
                """);

        assertValid(model);
        assertValid(none);
        assertThat(model.getModelSelection())
                .isInstanceOf(AgentTurnRequest.ModelModelSelectionRequest.class);
        assertThat(none.getModelSelection())
                .isInstanceOf(AgentTurnRequest.NoneModelSelectionRequest.class);
    }

    @Test
    void rejectsMissingOrInvalidModelSelectionShape() throws Exception {
        AgentTurnRequest missingSelection = mapper.readValue("""
                {
                  "requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}},
                  "conversationWindow":[]
                }
                """, AgentTurnRequest.class);
        AgentTurnRequest modelMissingRef = requestWithSelection("""
                {"kind":"MODEL","selectionVersion":"glm-4-7-flash-v1"}
                """);
        AgentTurnRequest modelMissingVersion = requestWithSelection("""
                {"kind":"MODEL","modelRef":"glm-4-7-flash"}
                """);

        assertThat(validator.validate(missingSelection))
                .extracting(ConstraintViolation::getMessage)
                .contains("modelSelection is required");
        assertThat(validator.validate(modelMissingRef)).isNotEmpty();
        assertThat(validator.validate(modelMissingVersion)).isNotEmpty();
        assertThatThrownBy(() -> requestWithSelection("""
                {"modelRef":"glm-4-7-flash","selectionVersion":"glm-4-7-flash-v1"}
                """)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> requestWithSelection("""
                {"kind":"NONE","modelRef":"glm-4-7-flash"}
                """)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> requestWithSelection("""
                {"kind":"MODEL","modelRef":"glm-4-7-flash",
                 "selectionVersion":"glm-4-7-flash-v1","endpoint":"https://example.invalid"}
                """)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> requestWithSelection("""
                {"kind":"DEFAULT"}
                """)).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsBlankPayloadsAndInvalidConversationOrder() throws Exception {
        AgentTurnRequest blank = request("""
                {"kind":"ASK","input":{"kind":"FREE_TEXT","text":" "}}
                """);
        assertThat(validator.validate(blank)).isNotEmpty();

        AgentTurnRequest invalidWindow = mapper.readValue("""
                {
                  "requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "modelSelection":{"kind":"NONE"},
                  "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}},
                  "conversationWindow":[
                    {"role":"ASSISTANT","content":"第一条不能是助手消息"},
                    {"role":"USER","content":"顺序错误"}
                  ]
                }
                """, AgentTurnRequest.class);
        assertThat(validator.validate(invalidWindow))
                .extracting(ConstraintViolation::getMessage)
                .contains("conversationWindow must alternate USER and ASSISTANT");
    }

    @Test
    void rejectsUnknownCommandAndInputKindsDuringDecode() {
        assertThatThrownBy(() -> request("""
                {"kind":"CONFIRM_PLAN"}
                """))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> request("""
                {"kind":"ASK","input":{"kind":"TASK_GRAPH"}}
                """))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsRetiredContinueShapeAndOperationFieldMismatch() throws Exception {
        AgentTurnRequest retired = request("""
                {"kind":"CONTINUE","contextHandle":"ctx_opaque",
                 "resultItemId":"item_opaque","text":"旧形状"}
                """);
        AgentTurnRequest mismatched = request("""
                {"kind":"CONTINUE","operation":"EXIT_CONTEXT",
                 "contextHandle":"ctx_opaque","text":"不允许"}
                """);

        assertThat(validator.validate(retired)).isNotEmpty();
        assertThat(validator.validate(mismatched)).isNotEmpty();
    }

    @Test
    void keepsOnlySmallSurfaceHintsAndBoundedWindow() throws Exception {
        AgentTurnRequest request = mapper.readValue("""
                {
                  "requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "modelSelection":{"kind":"MODEL","modelRef":"qwen-3-7-flash",
                    "selectionVersion":"qwen-3-7-flash-v1"},
                  "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"介绍这个项目"}},
                  "surfaceContext":{
                    "subjectHint":{"kind":"PROJECT","slug":"sql-audit"},
                    "audienceRole":"INTERVIEWER",
                    "requestSource":"AGENT_PAGE"
                  },
                  "conversationWindow":[
                    {"role":"USER","content":"上一轮问题"},
                    {"role":"ASSISTANT","content":"上一轮公开回答摘要"}
                  ]
                }
                """, AgentTurnRequest.class);

        assertValid(request);
        assertThat(request.getSurfaceContext().getSubjectHint().getKind())
                .isEqualTo(AgentTurnRequest.SubjectHintKind.PROJECT);
        assertThat(request.getConversationWindow()).hasSize(2);
    }

    private AgentTurnRequest request(String command) throws Exception {
        return mapper.readValue("""
                {
                  "requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "modelSelection":{"kind":"MODEL","modelRef":"glm-4-7-flash",
                    "selectionVersion":"glm-4-7-flash-v1"},
                  "command":%s,
                  "conversationWindow":[]
                }
                """.formatted(command), AgentTurnRequest.class);
    }

    private AgentTurnRequest requestWithSelection(String selection) throws Exception {
        return mapper.readValue("""
                {
                  "requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "modelSelection":%s,
                  "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}},
                  "conversationWindow":[]
                }
                """.formatted(selection), AgentTurnRequest.class);
    }

    private void assertValid(AgentTurnRequest request) {
        Set<ConstraintViolation<AgentTurnRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
