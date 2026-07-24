package com.portfolio.agent.answer.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerContextRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsExactlyOneProjectOrCaseSubject() {
        AnswerContextRequest projectContext = new AnswerContextRequest(
                "sql-audit",
                null,
                AudienceRole.GUEST,
                List.of(),
                AnswerRequestSource.AGENT_PAGE);
        AnswerContextRequest caseContext = new AnswerContextRequest(
                null,
                "codegraph-evaluation",
                AudienceRole.GUEST,
                List.of(),
                AnswerRequestSource.AGENT_PAGE);
        AnswerContextRequest mixedContext = new AnswerContextRequest(
                "sql-audit",
                "codegraph-evaluation",
                AudienceRole.GUEST,
                List.of(),
                AnswerRequestSource.AGENT_PAGE);
        AnswerContextRequest missingContext = new AnswerContextRequest(
                null,
                null,
                AudienceRole.GUEST,
                List.of(),
                AnswerRequestSource.AGENT_PAGE);

        assertThat(validator.validate(projectContext)).isEmpty();
        assertThat(validator.validate(caseContext)).isEmpty();
        assertThat(validator.validate(mixedContext))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("subjectSelectionValid");
        assertThat(validator.validate(missingContext))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("subjectSelectionValid");
    }
}
