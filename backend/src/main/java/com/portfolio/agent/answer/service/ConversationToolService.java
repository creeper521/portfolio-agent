package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationToolPlan;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.PublicKnowledgeTools;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConversationToolService {

    private static final Set<ToolKind> ALLOWED_TOOLS = EnumSet.allOf(ToolKind.class);
    private static final Set<ToolKind> SECOND_ROUND_TOOLS = EnumSet.of(
            ToolKind.GET_EVIDENCE_FOR_CLAIMS,
            ToolKind.GET_TIMELINE);

    private final ConversationalModelPort modelPort;
    private final PublicKnowledgeTools tools;
    private final int maxRounds;
    private final int maxCalls;
    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public ConversationToolService(
            ConversationalModelPort modelPort,
            PublicKnowledgeTools tools,
            int maxRounds,
            int maxCalls,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this.modelPort = modelPort;
        this.tools = tools;
        this.maxRounds = maxRounds;
        this.maxCalls = maxCalls;
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher,
                "diagnosticEventPublisher");
    }

    public PortfolioGroundingContext enrich(
            RuntimeAnswerContent content,
            String question,
            ConversationWindow window,
            ConversationRoute route,
            PortfolioGroundingContext initialGrounding
    ) {
        if (route.getIntent() != ConversationIntent.PORTFOLIO_GROUNDED
                && route.getIntent() != ConversationIntent.HYBRID) {
            return initialGrounding;
        }
        List<PublicToolResult> results = new ArrayList<>();
        int calls = 0;
        for (int round = 0; round < maxRounds && calls < maxCalls; round++) {
            List<ToolKind> allowed = List.copyOf(
                    round == 0 ? ALLOWED_TOOLS : SECOND_ROUND_TOOLS);
            long startedAt = System.nanoTime();
            ConversationModelResult<ConversationToolPlan> planned;
            try {
                planned = modelPort.planTools(
                        question,
                        window,
                        route,
                        merge(initialGrounding, results),
                        List.copyOf(results),
                        allowed);
            } catch (RuntimeException exception) {
                publishPlanResult(
                        round,
                        allowed.size(),
                        0,
                        ToolDiagnosticResultStatus.FAILURE,
                        ConversationModelFailureCode.INVALID_RESPONSE,
                        startedAt);
                throw exception;
            }
            if (planned == null || !planned.isSuccessful()) {
                publishPlanResult(
                        round,
                        allowed.size(),
                        0,
                        ToolDiagnosticResultStatus.FAILURE,
                        planned == null
                                ? ConversationModelFailureCode.INVALID_RESPONSE
                                : planned.getFailureCode(),
                        startedAt);
                break;
            }
            publishPlanResult(
                    round,
                    allowed.size(),
                    planned.getValue().getCalls().size(),
                    ToolDiagnosticResultStatus.SUCCESS,
                    null,
                    startedAt);
            for (ToolCall call : planned.getValue().getCalls()) {
                if (calls >= maxCalls || !allowed.contains(call.getKind())) {
                    break;
                }
                if (!isValidForRoute(call, route, initialGrounding)) {
                    continue;
                }
                long toolStartedAt = System.nanoTime();
                PublicToolResult result;
                try {
                    result = tools.execute(content, call);
                } catch (RuntimeException exception) {
                    publishToolExecutionFailure(call, toolStartedAt);
                    throw exception;
                }
                calls++;
                if (result == null
                        || result.getStatus() == PublicToolResultStatus.INSUFFICIENT) {
                    return merge(initialGrounding, results);
                }
                results.add(result);
            }
        }
        return merge(initialGrounding, results);
    }

    private void publishPlanResult(
            int zeroBasedRound,
            int allowedToolCount,
            int plannedCallCount,
            ToolDiagnosticResultStatus resultStatus,
            ConversationModelFailureCode failureCode,
            long startedAt
    ) {
        DiagnosticEvent.Builder builder = DiagnosticEvent.builder(
                        "tool.plan.completed",
                        failureCode == null
                                ? DiagnosticLevel.DEBUG
                                : DiagnosticLevel.WARN)
                .field("tool.round", zeroBasedRound + 1)
                .field("tool.allowed_count", allowedToolCount)
                .field("tool.planned_call_count", plannedCallCount)
                .field("tool.result_status", resultStatus)
                .field("duration.bucket", DurationBuckets.fromElapsedMillis(
                        (System.nanoTime() - startedAt) / 1_000_000L));
        if (failureCode != null) {
            builder.field(
                    "failure.code",
                    ProviderFailureCodeMapper.map(failureCode));
        }
        publishBestEffort(builder.build());
    }

    private void publishBestEffort(DiagnosticEvent event) {
        try {
            diagnosticEventPublisher.publish(event);
        } catch (RuntimeException ignored) {
            // Diagnostics must never change Tool planning.
        }
    }

    private void publishToolExecutionFailure(ToolCall call, long startedAt) {
        publishBestEffort(DiagnosticEvent.builder(
                        "tool.call.completed",
                        DiagnosticLevel.WARN)
                .field("tool.kind", call.getKind())
                .field("tool.result_status", ToolDiagnosticResultStatus.FAILURE)
                .field("tool.claim_count", 0)
                .field("tool.evidence_count", 0)
                .field("duration.bucket", DurationBuckets.fromElapsedMillis(
                        (System.nanoTime() - startedAt) / 1_000_000L))
                .field("failure.code", ToolFailureCode.TOOL_EXECUTION_FAILED)
                .build());
    }

    private boolean isValidForRoute(
            ToolCall call,
            ConversationRoute route,
            PortfolioGroundingContext grounding
    ) {
        if (!ALLOWED_TOOLS.contains(call.getKind())) {
            return false;
        }
        if (route.getProjectSlug() != null
                && (!call.getCaseSlugs().isEmpty()
                || call.getProjectSlugs().stream()
                        .anyMatch(slug -> !route.getProjectSlug().equals(slug)))) {
            return false;
        }
        if (route.getCaseSlug() != null
                && (!call.getProjectSlugs().isEmpty()
                || call.getCaseSlugs().stream()
                        .anyMatch(slug -> !route.getCaseSlug().equals(slug)))) {
            return false;
        }
        Set<String> allowedClaimIds = grounding.getClaims().stream()
                .map(AnswerClaimProjection::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return call.getClaimIds().stream().allMatch(allowedClaimIds::contains);
    }

    private PortfolioGroundingContext merge(
            PortfolioGroundingContext initial,
            List<PublicToolResult> results
    ) {
        Map<String, AnswerClaimProjection> claims = new LinkedHashMap<>();
        for (AnswerClaimProjection claim : initial.getClaims()) {
            claims.put(claim.getId(), claim);
        }
        Map<String, AnswerEvidence> evidence = new LinkedHashMap<>();
        for (AnswerEvidence item : initial.getEvidence()) {
            evidence.put(item.getId(), item);
        }
        for (PublicToolResult result : results) {
            for (AnswerClaimProjection claim : result.getClaims()) {
                claims.putIfAbsent(claim.getId(), claim);
            }
            for (AnswerEvidence item : result.getEvidence()) {
                evidence.putIfAbsent(item.getId(), item);
            }
        }
        return new PortfolioGroundingContext(
                initial.getSubject(),
                List.copyOf(claims.values()),
                List.copyOf(evidence.values()),
                initial.getChunks());
    }
}
