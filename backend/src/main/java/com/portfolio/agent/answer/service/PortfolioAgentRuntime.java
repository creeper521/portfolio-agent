package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerDecision;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.DurationBucket;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.QuestionKind;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.VerificationStatus;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public final class PortfolioAgentRuntime {

    private static final String BOUNDARY_MESSAGE =
            "当前版本只支持下方已发布问题。请选择一个受支持问题继续。";
    private static final String REJECTED_MESSAGE =
            "无法处理该请求。你可以改为询问已经公开的项目、职责、方案或验证信息。";

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final QuestionResolver questionResolver;
    private final AnswerContextFactory contextFactory;
    private final AnswerEngine answerEngine;
    private final VerificationPolicy verificationPolicy;
    private final AnswerDecisionPublisher decisionPublisher;

    public PortfolioAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            QuestionResolver questionResolver,
            AnswerContextFactory contextFactory,
            AnswerEngine answerEngine,
            VerificationPolicy verificationPolicy,
            AnswerDecisionPublisher decisionPublisher
    ) {
        this.knowledgeGateway = knowledgeGateway;
        this.questionResolver = questionResolver;
        this.contextFactory = contextFactory;
        this.answerEngine = answerEngine;
        this.verificationPolicy = verificationPolicy;
        this.decisionPublisher = decisionPublisher;
    }

    public AnswerResult answer(AnswerRequest request) {
        long startedAt = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        QuestionResolution resolution = questionResolver.resolve(content, request);
        AnswerTurnSnapshot turn = new AnswerTurnSnapshot(
                request.getTurnId(),
                requestId,
                content,
                resolution,
                request.getContext().getAudienceRole(),
                request.getContext().getSource()
        );
        ResolvedAnswerContext context = contextFactory.create(turn, resolution, request);
        AnswerResult result = buildResult(turn, resolution, context);
        publishBestEffort(result, request, startedAt);
        return result;
    }

    private AnswerResult buildResult(
            AnswerTurnSnapshot turn,
            QuestionResolution resolution,
            ResolvedAnswerContext context
    ) {
        List<String> suggestions = resolution.getProject().getQuestions().stream()
                .map(com.portfolio.agent.answer.domain.AnswerQuestion::getId)
                .toList();
        if (resolution.getResolution() != AnswerResolution.ANSWERED) {
            String message = resolution.getResolution() == AnswerResolution.REJECTED
                    ? REJECTED_MESSAGE
                    : BOUNDARY_MESSAGE;
            AnswerSectionType type = resolution.getResolution() == AnswerResolution.REJECTED
                    ? AnswerSectionType.REJECTED
                    : AnswerSectionType.BOUNDARY;
            return new AnswerResult(
                    turn,
                    resolution.getResolution(),
                    null,
                    GenerationMode.DETERMINISTIC,
                    VerificationStatus.NOT_APPLICABLE,
                    resolution.getProject().getTitle(),
                    message,
                    List.of(new AnswerSection(type, "能力说明", message, List.of())),
                    List.of(),
                    suggestions
            );
        }

        GeneratedAnswer generated = answerEngine.answer(context);
        VerificationStatus verification = verificationPolicy.verify(
                resolution.getResolution(), context, generated);
        List<String> evidenceIds = context.getApprovedEvidence().stream()
                .map(com.portfolio.agent.answer.domain.AnswerEvidence::getId)
                .distinct()
                .toList();
        return new AnswerResult(
                turn,
                AnswerResolution.ANSWERED,
                AnswerSource.PRESET,
                GenerationMode.DETERMINISTIC,
                verification,
                generated.getTitle(),
                generated.getSummary(),
                generated.getSections(),
                evidenceIds,
                suggestions
        );
    }

    private void publishBestEffort(AnswerResult result, AnswerRequest request, long startedAt) {
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        DurationBucket bucket = durationBucket(elapsedMillis);
        QuestionKind kind = request.getQuestionPresetId() == null
                ? QuestionKind.FREE_TEXT
                : QuestionKind.PRESET;
        try {
            decisionPublisher.publish(new AnswerDecision(
                    Instant.now(), result.getTurnSnapshot(), kind, result, bucket, null));
        } catch (RuntimeException ignored) {
            // Observability is passive and must never change the visitor response.
        }
    }

    private DurationBucket durationBucket(long elapsedMillis) {
        if (elapsedMillis < 100) {
            return DurationBucket.LT_100_MS;
        }
        if (elapsedMillis < 500) {
            return DurationBucket.FROM_100_TO_499_MS;
        }
        if (elapsedMillis < 2000) {
            return DurationBucket.FROM_500_TO_1999_MS;
        }
        return DurationBucket.GE_2000_MS;
    }
}
