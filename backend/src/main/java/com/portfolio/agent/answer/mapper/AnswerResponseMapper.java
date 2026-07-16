package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.response.AnswerPayload;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.dto.response.AnswerSectionResponse;
import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
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

        List<EvidenceResponse> evidence = result.getEvidence().stream()
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

    private EvidenceResponse toEvidenceResponse(AnswerEvidence evidence) {
        return new EvidenceResponse(
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
