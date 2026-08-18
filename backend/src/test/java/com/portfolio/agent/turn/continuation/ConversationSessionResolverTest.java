package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationSessionResolverTest {
    @Test void firstRequestIsDeterministicUntilCommittedThenBearerAuthenticates() {
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore();
        ConversationSessionResolver resolver = new ConversationSessionResolver(
                store, new byte[32], Clock.fixed(
                Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(30));
        UUID requestId = UUID.randomUUID();
        ConversationSessionResolver.Resolution first = resolver.resolve(null, requestId);
        ConversationSessionResolver.Resolution retry = resolver.resolve(null, requestId);
        assertThat(retry.conversationId()).isEqualTo(first.conversationId());
        assertThat(retry.issuedToken()).isEqualTo(first.issuedToken());
        resolver.commit(first);
        ConversationSessionResolver.Resolution authenticated =
                resolver.resolve(first.issuedToken().encode(), UUID.randomUUID());
        assertThat(authenticated.status()).isEqualTo(ConversationSessionResolver.Status.AUTHENTICATED);
        assertThat(authenticated.conversationId()).isEqualTo(first.conversationId());
        assertThat(authenticated.issuedToken()).isNull();
    }

    @Test void malformedOrUnknownBearerFailsClosed() {
        ConversationSessionResolver resolver = new ConversationSessionResolver(
                new InMemoryConversationSessionStore(), new byte[32], Clock.systemUTC(), Duration.ofMinutes(30));
        assertThat(resolver.resolve("not-a-token", UUID.randomUUID()).status())
                .isEqualTo(ConversationSessionResolver.Status.INVALID);
        assertThat(resolver.resolve(ResumeToken.of(new byte[32]).encode(), UUID.randomUUID()).status())
                .isEqualTo(ConversationSessionResolver.Status.INVALID);
    }
}
