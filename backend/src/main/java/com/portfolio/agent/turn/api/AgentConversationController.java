package com.portfolio.agent.turn.api;

import com.portfolio.agent.turn.api.response.AgentApiErrorResponse;
import com.portfolio.agent.turn.api.response.ConversationSummaryResponse;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/conversations/current")
public final class AgentConversationController {
    private final AgentTurnLifecycleService lifecycle;
    public AgentConversationController(AgentTurnLifecycleService lifecycle) { this.lifecycle = lifecycle; }

    @GetMapping
    public ResponseEntity<?> current(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AgentTurnController.Bearer bearer = AgentTurnController.bearer(authorization, true);
        if (!bearer.valid()) return unauthorized();
        AgentTurnLifecycleService.ConversationStatus status =
                lifecycle.currentConversation(bearer.token());
        if (!status.authenticated()) return unauthorized();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ConversationSummaryResponse(
                        status.conversationId(), status.discussionRevision(),
                        status.discussion()));
    }

    @DeleteMapping
    public ResponseEntity<?> clear(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AgentTurnController.Bearer bearer = AgentTurnController.bearer(authorization, true);
        if (!bearer.valid() || !lifecycle.clearConversation(bearer.token())) return unauthorized();
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
    private ResponseEntity<AgentApiErrorResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AgentApiErrorResponse.of(
                        null, "RESUME_TOKEN_INVALID", "会话凭证无效或已过期。", false, null));
    }
}
