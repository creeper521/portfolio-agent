package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.response.AnswerEvidenceResponse;
import com.portfolio.agent.answer.dto.response.AnswerPayload;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.dto.response.AnswerSectionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnswerResponseMapper {

    public AnswerResponse toResponse(String requestId, AnswerResult result) {
        List<AnswerSectionResponse> sections = result.getSections().stream()
                .map(section -> new AnswerSectionResponse(
                        section.getType(),
                        section.getContent()
                ))
                .toList();

        List<AnswerEvidenceResponse> evidence = result.getEvidence().stream()
                .map(this::toEvidenceResponse)
                .toList();

        return new AnswerResponse(
                requestId,
                result.getAnswerMode(),
                result.isMatched(),
                result.isFallback(),
                new AnswerPayload(result.getTitle(), sections),
                evidence,
                result.getSuggestedQuestions()
        );
    }

    private AnswerEvidenceResponse toEvidenceResponse(AnswerEvidence evidence) {
        return new AnswerEvidenceResponse(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getType(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getSupportedClaims(),
                evidence.getPublicStatus(),
                evidence.isRawContentPublic()
        );
    }
}
