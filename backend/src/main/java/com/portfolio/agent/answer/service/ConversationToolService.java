package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.ConversationIntent;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public ConversationToolService(
            ConversationalModelPort modelPort,
            PublicKnowledgeTools tools,
            int maxRounds,
            int maxCalls
    ) {
        this.modelPort = modelPort;
        this.tools = tools;
        this.maxRounds = maxRounds;
        this.maxCalls = maxCalls;
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
            ConversationModelResult<ConversationToolPlan> planned = modelPort.planTools(
                    question,
                    window,
                    route,
                    merge(initialGrounding, results),
                    List.copyOf(results),
                    allowed);
            if (planned == null || !planned.isSuccessful()) {
                break;
            }
            for (ToolCall call : planned.getValue().getCalls()) {
                if (calls >= maxCalls || !allowed.contains(call.getKind())) {
                    break;
                }
                if (!isValidForRoute(call, route, initialGrounding)) {
                    continue;
                }
                PublicToolResult result = tools.execute(content, call);
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
