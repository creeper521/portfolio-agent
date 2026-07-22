package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.dto.response.AnswerSectionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class AnswerResponseMapper {

    public AnswerResponse toResponse(AnswerResult result) {
        List<AnswerSectionResponse> sections = result.getSections().stream()
                .map(section -> new AnswerSectionResponse(
                        section.getType(),
                        section.getTitle(),
                        section.getContent(),
                        section.getEvidenceIds(),
                        section.getClaimIds()
                ))
                .toList();
        return new AnswerResponse(
                result.getTurnSnapshot().getRequestId(),
                result.getTurnSnapshot().getTurnId(),
                result.getTurnSnapshot().getContentVersion(),
                result.getTurnSnapshot().getQuestionPresetId(),
                result.getResolution(),
                result.getAnswerSource(),
                result.getGenerationMode(),
                result.getVerification(),
                result.getTitle(),
                result.getSummary(),
                sections,
                result.getEvidenceIds(),
                result.getSuggestedQuestionPresetIds()
        );
    }
}
