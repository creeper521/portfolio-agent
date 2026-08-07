package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP E2E layer that posts the sanitized eval question to the running backend
 * through the JDK answer client. Connection information comes only from the
 * explicit base url and is never written to the report.
 */
public final class HttpEvalExecutor implements EvalExecutor {

    private final EvalAnswerClient client;
    private final String baseUrl;
    private final RuntimeContentSnapshot bundle;

    public HttpEvalExecutor(EvalAnswerClient client, String baseUrl) {
        this(client, baseUrl, null);
    }

    public HttpEvalExecutor(
            EvalAnswerClient client,
            String baseUrl,
            RuntimeContentSnapshot bundle) {
        this.client = Objects.requireNonNull(client, "client");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.bundle = bundle;
    }

    @Override
    public boolean supports(EvalLayer layer) {
        return layer == EvalLayer.HTTP_E2E;
    }

    @Override
    public EvalObservation execute(EvalExecutionInput input, EvalRunContext context) {
        String question = firstUserMessage(input.getMessages());
        if (question == null || question.isBlank()) {
            return error(input, "CLIENT_INVALID");
        }
        EvalHttpResult result = client.answer(new EvalHttpRequest(
                baseUrl, input.getCaseId(), input.getCaseId(), question));
        if (result.getFailureCode() == EvalHttpResult.FailureCode.NONE
                && result.getStatusCode() == 200
                && result.getResolution() == AnswerResolution.ANSWERED) {
            return new EvalObservation(
                    input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                    EvalObservationStatus.PASS,
                    selectedProjectSlug(result.getClaimIds()), selectedCaseSlug(result.getClaimIds()),
                    result.getClaimIds(), result.getEvidenceIds(), List.of(),
                    result.getResolution(), result.getAnswerScope(),
                    result.getGenerationMode(), result.getAnswerSource(),
                    List.of("HTTP_ANSWERED"), result.getDurationMilliseconds(),
                    EvalProviderUsage.unavailable(), result.getAnswerShape(),
                    result.isDegraded(), false);
        }
        EvalObservationStatus status = result.getFailureCode()
                == EvalHttpResult.FailureCode.TRANSPORT_FAILURE
                || result.getFailureCode() == EvalHttpResult.FailureCode.TIMEOUT
                ? EvalObservationStatus.ERROR : EvalObservationStatus.FAIL;
        String reason = switch (result.getFailureCode()) {
            case TIMEOUT -> "CLIENT_TIMEOUT";
            case INVALID_JSON -> "CLIENT_INVALID_RESPONSE";
            case POLICY_LEAK -> "CLIENT_POLICY_LEAK";
            case HTTP_ERROR -> "HTTP_" + result.getStatusCode();
            case CLIENT_ERROR -> "CLIENT_ERROR";
            case TRANSPORT_FAILURE -> "CLIENT_NETWORK_ERROR";
            case NONE -> result.getResolution().name();
        };
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(), status,
                null, null, List.of(), List.of(), List.of(),
                result.getResolution(), result.getAnswerScope(),
                result.getGenerationMode(), result.getAnswerSource(),
                List.of(reason), result.getDurationMilliseconds(),
                EvalProviderUsage.unavailable(), EvalAnswerShape.empty(),
                result.isDegraded(), false);
    }

    private String selectedProjectSlug(List<String> claimIds) {
        return selectedSlug(claimIds, ClaimSubjectType.PROJECT);
    }

    private String selectedCaseSlug(List<String> claimIds) {
        return selectedSlug(claimIds, ClaimSubjectType.CASE);
    }

    private String selectedSlug(List<String> claimIds, ClaimSubjectType type) {
        if (bundle == null) {
            return null;
        }
        for (Claim claim : bundle.getClaims()) {
            if (type != claim.getSubjectType() || !claimIds.contains(claim.getId())) {
                continue;
            }
            if (type == ClaimSubjectType.PROJECT) {
                for (com.portfolio.agent.portfolio.domain.ProjectProfile project : bundle.getProjects()) {
                    if (project.getId().equals(claim.getSubjectId())) {
                        return project.getSlug();
                    }
                }
            } else {
                for (com.portfolio.agent.portfolio.domain.CaseStudy subject : bundle.getCases()) {
                    if (subject.getId().equals(claim.getSubjectId())) {
                        return subject.getSlug();
                    }
                }
            }
        }
        return null;
    }

    private String firstUserMessage(List<EvalMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(0).getContent();
    }

    private EvalObservation error(EvalExecutionInput input, String reasonCode) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.ERROR,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.INVALID_INPUT, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(reasonCode), 0L,
                EvalProviderUsage.unavailable(), EvalAnswerShape.empty(),
                false, false);
    }
}
