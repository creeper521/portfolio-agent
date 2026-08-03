package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import com.portfolio.agent.answer.domain.ConversationTopic;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsTwentyAlternatingConversationRounds() {
        ConversationAnswerRequest request = request(history(20));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getRequestToken()).isEqualTo(
                UUID.fromString("6b2d8895-4108-4b4d-aee0-21f6e7c4f333"));
    }

    @Test
    void rejectsMoreThanTwentyConversationRounds() {
        ConversationAnswerRequest request = request(history(21));

        Set<ConstraintViolation<ConversationAnswerRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("messages must contain at most 20 rounds");
    }

    @Test
    void rejectsNonAlternatingHistory() {
        ConversationAnswerRequest request = request(List.of(
                new ConversationMessageRequest(
                        ConversationMessageRole.USER, "first question"),
                new ConversationMessageRequest(
                        ConversationMessageRole.USER, "second question")));

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("messages must alternate USER and ASSISTANT");
    }

    @Test
    void rejectsSimultaneousProjectAndCaseHints() {
        ConversationAnswerRequest request = new ConversationAnswerRequest(
                "turn-1",
                "visitor question",
                List.of(),
                new ConversationAnswerContextRequest(
                        "sql-audit",
                        "codegraph-evaluation",
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.AGENT_PAGE));

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("projectSlug and caseSlug cannot both be set");
    }

    @Test
    void deserializesCaseSourceAndCaseSlug() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        ConversationAnswerRequest request = objectMapper.readValue("""
                {
                  "turnId": "turn-case",
                  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "question": "Tell me about this case",
                  "messages": [],
                  "context": {
                    "caseSlug": "multilingual-image-preservation",
                    "audienceRole": "INTERVIEWER",
                    "source": "CASE"
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(request.getContext().getSource()).isEqualTo(AnswerRequestSource.CASE);
        assertThat(request.getContext().getCaseSlug())
                .isEqualTo("multilingual-image-preservation");
    }

    @Test
    void deserializesCoveredTopicsWithoutPersistingConversationText()
            throws Exception {
        ConversationAnswerRequest request = new ObjectMapper().readValue("""
                {
                  "turnId": "turn-progress",
                  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "question": "继续介绍",
                  "messages": [],
                  "context": {
                    "projectSlug": "sql-audit",
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE",
                    "coveredTopics": ["BACKGROUND", "SOLUTION"]
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(request.getContext().getCoveredTopics())
                .containsExactly(
                        ConversationTopic.BACKGROUND,
                        ConversationTopic.SOLUTION);
    }

    @Test
    void deserializesRecommendationContextForStatelessRecommendationRefinement()
            throws Exception {
        ConversationAnswerRequest request = new ObjectMapper().readValue("""
                {
                  "turnId": "turn-refinement",
                  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "question": "Replace the first one",
                  "messages": [],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE",
                    "recommendationContext": {
                      "recommendationBatchId": "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                      "contentVersion": "public-2026-07-31",
                      "careerTrack": "BACKEND",
                      "audienceRole": "INTERVIEWER",
                      "capabilityCodes": ["POSTGRESQL", "RAG"],
                      "requestedSize": 2,
                      "selectedPortfolioIds": ["project-1", "case-2"]
                    }
                  }
                }
                """, ConversationAnswerRequest.class);

        PortfolioRecommendationContextRequest recommendationContext = request.getContext()
                .getRecommendationContext();
        assertThat(recommendationContext.getRecommendationBatchId()).startsWith("rec_");
        assertThat(recommendationContext.getSelectedPortfolioIds())
                .containsExactly("project-1", "case-2");
    }

    @Test
    void deserializesPresetIdAndExplicitPortfolioReferenceContext()
            throws Exception {
        ConversationAnswerRequest request = new ObjectMapper().readValue("""
                {
                  "turnId": "turn-reference",
                  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "questionPresetId": "question-sql-audit-async-and-recovery",
                  "question": "Show the evidence for this conclusion",
                  "messages": [],
                  "context": {
                    "projectSlug": "sql-audit",
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE",
                    "referenceContext": {
                      "previousContentVersion": "public-2026-07-31",
                      "projectSlugs": ["sql-audit"],
                      "caseSlugs": [],
                      "questionPresetId": "question-sql-audit-async-and-recovery",
                      "referencedClaimIds": ["claim-sql-audit-async-task"],
                      "selectedSectionType": "VERIFICATION",
                      "followUpAction": "SHOW_EVIDENCE"
                    }
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(request.getQuestionPresetId())
                .isEqualTo("question-sql-audit-async-and-recovery");
        assertThat(request.getContext().getReferenceContext().getFollowUpAction())
                .isEqualTo(PortfolioFollowUpAction.SHOW_EVIDENCE);
        assertThat(request.getContext().getReferenceContext().getReferencedClaimIds())
                .containsExactly("claim-sql-audit-async-task");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsInvalidRecommendationContextFromJson() throws Exception {
        ConversationAnswerRequest request = new ObjectMapper().readValue("""
                {
                  "turnId": "turn-refinement",
                  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "question": "Replace the first one",
                  "messages": [],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE",
                    "recommendationContext": {
                      "recommendationBatchId": "rec_not-a-sha256",
                      "contentVersion": "",
                      "audienceRole": "INTERVIEWER",
                      "capabilityCodes": [],
                      "requestedSize": 6,
                      "selectedPortfolioIds": []
                    }
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "recommendationBatchId format is invalid",
                        "contentVersion is required",
                        "requestedSize must be between 2 and 5");
    }

    @Test
    void rejectsMissingRecommendationContextCollections() throws Exception {
        ConversationAnswerRequest request = new ObjectMapper().readValue("""
                {
                  "turnId": "turn-refinement",
                  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "question": "Replace the first one",
                  "messages": [],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE",
                    "recommendationContext": {
                      "recommendationBatchId": "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                      "contentVersion": "public-2026-07-31",
                      "audienceRole": "INTERVIEWER",
                      "requestedSize": 2
                    }
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("capabilityCodes is required", "selectedPortfolioIds is required");
    }

    @Test
    void rejectsExplicitNullRecommendationContextCollections() throws Exception {
        ConversationAnswerRequest request = new ObjectMapper().readValue("""
                {
                  "turnId": "turn-refinement",
                  "requestToken": "6b2d8895-4108-4b4d-aee0-21f6e7c4f333",
                  "question": "Replace the first one",
                  "messages": [],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE",
                    "recommendationContext": {
                      "recommendationBatchId": "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                      "contentVersion": "public-2026-07-31",
                      "audienceRole": "INTERVIEWER",
                      "capabilityCodes": null,
                      "requestedSize": 2,
                      "selectedPortfolioIds": null
                    }
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("capabilityCodes is required", "selectedPortfolioIds is required");
    }

    @Test
    void rejectsMissingRequestTokenFromJson() throws Exception {
        ConversationAnswerRequest request = new ObjectMapper().readValue("""
                {
                  "turnId": "turn-case",
                  "question": "Tell me about this case",
                  "messages": [],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE"
                  }
                }
                """, ConversationAnswerRequest.class);

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("requestToken is required");
    }

    @Test
    void redactsVisitorTextFromDiagnostics() {
        ConversationAnswerRequest request = request(List.of(
                new ConversationMessageRequest(
                        ConversationMessageRole.USER, "history-secret"),
                new ConversationMessageRequest(
                        ConversationMessageRole.ASSISTANT, "answer-secret")));

        assertThat(request.toString())
                .doesNotContain("visitor question", "history-secret", "answer-secret")
                .contains("question='<redacted>'", "messageCount=2");
    }

    private ConversationAnswerRequest request(List<ConversationMessageRequest> messages) {
        return new ConversationAnswerRequest(
                "turn-1",
                UUID.fromString("6b2d8895-4108-4b4d-aee0-21f6e7c4f333"),
                "visitor question",
                messages,
                new ConversationAnswerContextRequest(
                        null,
                        null,
                        AudienceRole.GUEST,
                        AnswerRequestSource.AGENT_PAGE));
    }

    private List<ConversationMessageRequest> history(int rounds) {
        List<ConversationMessageRequest> messages = new ArrayList<>();
        for (int index = 0; index < rounds; index++) {
            messages.add(new ConversationMessageRequest(
                    ConversationMessageRole.USER, "question-" + index));
            messages.add(new ConversationMessageRequest(
                    ConversationMessageRole.ASSISTANT, "answer-" + index));
        }
        return messages;
    }
}
