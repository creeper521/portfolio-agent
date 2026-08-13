package com.portfolio.agent.answer.context.adapter.postgres;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;

/** Scheduled cleanup entry point; it has no in-memory fallback. */
public final class ConversationContextCleanupJob {
    private final ConversationContextCleanupService cleanupService;
    private final Clock clock;

    public ConversationContextCleanupJob(ConversationContextCleanupService cleanupService) {
        this(cleanupService, Clock.systemUTC());
    }

    ConversationContextCleanupJob(ConversationContextCleanupService cleanupService, Clock clock) {
        this.cleanupService = cleanupService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 900_000L)
    public void run() {
        cleanupService.cleanupExpired(Instant.now(clock));
    }
}
