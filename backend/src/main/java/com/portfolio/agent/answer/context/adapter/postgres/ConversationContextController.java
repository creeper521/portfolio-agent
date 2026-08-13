package com.portfolio.agent.answer.context.adapter.postgres;

import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.service.ConversationContextFacade;
import com.portfolio.agent.answer.dto.response.ConversationContextSummaryResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** PostgreSQL-backed Context refresh/clear API; every response is explicitly no-store. */
@RestController
@RequestMapping("/api/v2/conversation-context")
@ConditionalOnProperty(
        prefix = "portfolio.conversation-context", name = "mode", havingValue = "POSTGRESQL")
public final class ConversationContextController {
    public static final String RESUME_TOKEN_HEADER = "X-Conversation-Resume-Token";
    private final ConversationContextFacade facade;

    public ConversationContextController(ConversationContextFacade facade) {
        this.facade = facade;
    }

    @GetMapping
    public ResponseEntity<ConversationContextSummaryResponse> get(
            @RequestHeader(name = RESUME_TOKEN_HEADER, required = true) String encodedToken) {
        ResumeToken token = parse(encodedToken);
        ConversationContextSummaryResponse response = facade.summary(token, Instant.now())
                .map(ConversationContextSummaryResponse::available)
                .orElseGet(() -> ConversationContextSummaryResponse.unavailable(
                        ConversationContinuationStatus.CONTEXT_EXPIRED));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(
            @RequestHeader(name = RESUME_TOKEN_HEADER, required = true) String encodedToken) {
        facade.clear(parse(encodedToken));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResumeToken parse(String encodedToken) {
        try {
            return ResumeToken.fromBase64Url(encodedToken);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "INVALID_CONVERSATION_RESUME_TOKEN");
        }
    }
}
