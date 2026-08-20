package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationSessionResolverTest {
    @Test void firstRequestIsDeterministicUntilCommittedThenBearerAuthenticates() {
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore();
        ConversationSessionResolver resolver = new ConversationSessionResolver(
                store, new byte[32], Clock.fixed(
                Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(30));
        UUID requestId = UUID.randomUUID();
        ConversationSessionResolver.Resolution first = resolver.resolve(null, requestId, deadline(
                Instant.parse("2026-08-18T00:00:00Z")));
        ConversationSessionResolver.Resolution retry = resolver.resolve(null, requestId, deadline(
                Instant.parse("2026-08-18T00:00:00Z")));
        assertThat(retry.conversationId()).isEqualTo(first.conversationId());
        assertThat(retry.issuedToken()).isNotEqualTo(first.issuedToken());
        store.save(resolver.pendingSession(first));
        ConversationSessionResolver.Resolution authenticated =
                resolver.resolve(first.issuedToken().encode(), UUID.randomUUID(), deadline(
                        Instant.parse("2026-08-18T00:00:00Z")));
        assertThat(authenticated.status()).isEqualTo(ConversationSessionResolver.Status.AUTHENTICATED);
        assertThat(authenticated.conversationId()).isEqualTo(first.conversationId());
        assertThat(authenticated.issuedToken()).isNull();
    }

    @Test void malformedOrUnknownBearerFailsClosed() {
        ConversationSessionResolver resolver = new ConversationSessionResolver(
                new InMemoryConversationSessionStore(), new byte[32], Clock.systemUTC(), Duration.ofMinutes(30));
        assertThat(resolver.resolve("not-a-token", UUID.randomUUID(), deadline(Instant.now())).status())
                .isEqualTo(ConversationSessionResolver.Status.INVALID);
        assertThat(resolver.resolve(ResumeToken.of(new byte[32]).encode(), UUID.randomUUID(),
                deadline(Instant.now())).status())
                .isEqualTo(ConversationSessionResolver.Status.INVALID);
    }

    @Test void previousTokenKeyAuthenticatesUntilTheOriginalAbsoluteExpiry() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore();
        byte[] previous = new byte[32];
        java.util.Arrays.fill(previous, (byte) 1);
        byte[] current = new byte[32];
        java.util.Arrays.fill(current, (byte) 2);
        ConversationSessionResolver oldResolver = new ConversationSessionResolver(
                store, previous, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(30));
        ConversationSessionResolver.Resolution issued = oldResolver.resolve(
                null, UUID.randomUUID(), deadline(now));
        store.save(oldResolver.pendingSession(issued));
        ConversationSessionResolver rotatedResolver = new ConversationSessionResolver(
                store, current, List.of(previous),
                Clock.fixed(now.plus(Duration.ofMinutes(29)), ZoneOffset.UTC),
                Duration.ofMinutes(30));

        assertThat(rotatedResolver.resolve(
                issued.issuedToken().encode(), UUID.randomUUID(), deadline(
                        now.plus(Duration.ofMinutes(29)))).status())
                .isEqualTo(ConversationSessionResolver.Status.AUTHENTICATED);
        ConversationSessionResolver expiredResolver = new ConversationSessionResolver(
                store, current, List.of(previous),
                Clock.fixed(now.plus(Duration.ofMinutes(30)), ZoneOffset.UTC),
                Duration.ofMinutes(30));
        assertThat(expiredResolver.resolve(
                issued.issuedToken().encode(), UUID.randomUUID(), deadline(
                        now.plus(Duration.ofMinutes(30)))).status())
                .isEqualTo(ConversationSessionResolver.Status.INVALID);
    }

    @Test void inMemoryTokenRotationCannotExtendTheOriginalSessionExpiry() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore();
        String conversationId = UUID.randomUUID().toString();
        byte[] firstHash = new byte[32];
        byte[] rotatedHash = new byte[32];
        rotatedHash[0] = 1;
        store.save(new ConversationSessionStore.Session(
                conversationId, firstHash, now, now.plus(Duration.ofMinutes(30))));
        store.save(new ConversationSessionStore.Session(
                conversationId, rotatedHash, now.plus(Duration.ofMinutes(10)),
                now.plus(Duration.ofMinutes(40))));

        assertThat(find(store, firstHash, now.plus(Duration.ofMinutes(11)))).isEmpty();
        assertThat(find(store, rotatedHash, now.plus(Duration.ofMinutes(29)))).isPresent();
        assertThat(find(store, rotatedHash, now.plus(Duration.ofMinutes(30)))).isEmpty();
    }

    @Test void revokedInMemorySessionCannotBeResurrectedByLateSettlement() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore();
        String conversationId = UUID.randomUUID().toString();
        byte[] tokenHash = new byte[32];
        ConversationSessionStore.Session session = new ConversationSessionStore.Session(
                conversationId, tokenHash, now, now.plus(Duration.ofMinutes(30)));
        store.save(session);

        store.revokeForTest(conversationId);
        store.save(session);

        assertThat(find(store, tokenHash, now.plusSeconds(1))).isEmpty();
    }

    @Test void newerInMemoryTentativeSessionReplacesExpiredOrRevokedState() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore();
        String conversationId = UUID.randomUUID().toString();
        byte[] oldHash = new byte[32];
        byte[] replacementHash = new byte[32];
        replacementHash[0] = 1;
        store.save(new ConversationSessionStore.Session(
                conversationId, oldHash, now, now.plus(Duration.ofMinutes(30))));
        store.save(new ConversationSessionStore.Session(
                conversationId, replacementHash, now.plus(Duration.ofMinutes(30)),
                now.plus(Duration.ofMinutes(60))));
        assertThat(find(store, oldHash, now.plus(Duration.ofMinutes(31)))).isEmpty();
        assertThat(find(store, replacementHash, now.plus(Duration.ofMinutes(59)))).isPresent();

        store.revokeIfMatches(
                conversationId, replacementHash, now.plus(Duration.ofMinutes(59)));
        byte[] afterRevokeHash = new byte[32];
        afterRevokeHash[0] = 2;
        store.save(new ConversationSessionStore.Session(
                conversationId, afterRevokeHash, now.plus(Duration.ofMinutes(60)),
                now.plus(Duration.ofMinutes(90))));
        assertThat(find(store, replacementHash, now.plus(Duration.ofMinutes(59)).plusSeconds(2)))
                .isEmpty();
        assertThat(find(store, afterRevokeHash, now.plus(Duration.ofMinutes(60)).plusSeconds(1)))
                .isPresent();
    }

    private com.portfolio.agent.turn.execution.TurnDeadline deadline(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new com.portfolio.agent.turn.execution.TurnDeadline(now.plusSeconds(5), clock);
    }
    private java.util.Optional<ConversationSessionStore.Session> find(
            ConversationSessionStore store, byte[] hash, Instant now) {
        return store.find(List.of(hash), now, deadline(now));
    }
}
