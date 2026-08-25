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

/**
 * 四条无版本 Agent HTTP 资源中的会话资源：GET/DELETE
 * /api/agent/conversations/current。
 *
 * <p>两个端点都强制要求 ResumeToken 凭证（缺失即 401），响应用于匿名会话的
 * 状态展示与主动清空；均为只读或一次性清除，不产生新的 Turn。</p>
 */
@RestController
@RequestMapping("/api/agent/conversations/current")
public final class AgentConversationController {
    private final AgentTurnLifecycleService lifecycle;
    public AgentConversationController(AgentTurnLifecycleService lifecycle) { this.lifecycle = lifecycle; }

    /** 查询当前会话摘要：会话 ID、讨论修订号与活跃讨论（无有效凭证时 401）。 */
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

    /** 清空当前匿名会话：成功 204；凭证无效或会话已不存在返回 401。 */
    @DeleteMapping
    public ResponseEntity<?> clear(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AgentTurnController.Bearer bearer = AgentTurnController.bearer(authorization, true);
        if (!bearer.valid() || !lifecycle.clearConversation(bearer.token())) return unauthorized();
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
    /** 统一 401 响应；错误体不携带 requestId。 */
    private ResponseEntity<AgentApiErrorResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AgentApiErrorResponse.of(
                        null, "RESUME_TOKEN_INVALID", "会话凭证无效或已过期。", false, null));
    }
}
