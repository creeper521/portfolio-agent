package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.dto.response.AnswerResponse;
import com.portfolio.agent.answer.dto.response.AnswerSectionResponse;
import com.portfolio.agent.answer.dto.response.ContextEnvelopeResponse;
import com.portfolio.agent.answer.domain.AnswerResolution;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;

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
        ContextEnvelopeResponse contextEnvelope = createContextEnvelope(result, sections);
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
                result.getSuggestedQuestionPresetIds(),
                contextEnvelope,
                result.isContextVersionUpdated()
        );
    }

    private ContextEnvelopeResponse createContextEnvelope(
            AnswerResult result,
            List<AnswerSectionResponse> sections
    ) {
        if (result.getResolution() != AnswerResolution.ANSWERED) {
            return null;
        }
        List<String> claimIds = boundedSectionClaims(sections);
        return new ContextEnvelopeResponse(
                result.getTurnSnapshot().getContentVersion(),
                List.of(result.getTurnSnapshot().getProjectSlug()),
                result.getTurnSnapshot().getQuestionPresetId(),
                claimIds,
                null,
                null);
    }

    private List<String> boundedSectionClaims(List<AnswerSectionResponse> sections) {
        LinkedHashSet<String> claimIds = new LinkedHashSet<>();
        for (AnswerSectionResponse section : sections) {
            if (claimIds.size() == 8) {
                return List.copyOf(claimIds);
            }
            if (!section.getClaimIds().isEmpty()) {
                claimIds.add(section.getClaimIds().getFirst());
            }
        }
        for (AnswerSectionResponse section : sections) {
            for (String claimId : section.getClaimIds()) {
                if (claimIds.size() == 8) {
                    return List.copyOf(claimIds);
                }
                claimIds.add(claimId);
            }
        }
        return List.copyOf(claimIds);
    }
}
