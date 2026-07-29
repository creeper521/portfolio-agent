package com.portfolio.agent.answer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import com.portfolio.agent.answer.exception.AnswerAdmissionRejectedException;
import com.portfolio.agent.answer.exception.AnswerErrorCode;

public final class AnswerIdempotencyCoordinator<T> {

    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(1);
    private static final int CLEANUP_BATCH_SIZE = 256;
    private final Clock clock;
    private final Duration ttl;
    private final int maxEntries;
    private final ConcurrentHashMap<RequestKey, Entry<T>> entries = new ConcurrentHashMap<>();
    private final Deque<EntryExpiry> expiries = new ArrayDeque<>();
    private Instant nextCleanupAt = Instant.MIN;

    public AnswerIdempotencyCoordinator(Clock clock, Duration ttl) {
        this(clock, ttl, 20_000);
    }

    AnswerIdempotencyCoordinator(Clock clock, Duration ttl, int maxEntries) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    public T execute(String sourceHash, UUID requestToken, Supplier<T> operation) {
        var key = new RequestKey(
                Objects.requireNonNull(sourceHash, "sourceHash must not be null"),
                Objects.requireNonNull(requestToken, "requestToken must not be null")
        );
        Objects.requireNonNull(operation, "operation must not be null");

        while (true) {
            var now = clock.instant();
            Entry<T> selected;
            boolean producer;
            synchronized (entries) {
                cleanupExpired(now, false);
                var existing = entries.get(key);
                if (existing == null) {
                    if (entries.size() >= maxEntries) {
                        cleanupExpired(now, true);
                    }
                    if (entries.size() >= maxEntries) {
                        throw new AnswerAdmissionRejectedException(
                                AnswerErrorCode.ANSWER_RATE_LIMITED, 60);
                    }
                    selected = new Entry<>();
                    entries.put(key, selected);
                    producer = true;
                } else {
                    selected = existing;
                    producer = false;
                }
            }
            if (producer) {
                produce(key, selected, operation);
            }
            return await(selected.result);
        }
    }

    private void cleanupExpired(Instant now, boolean force) {
        if (!force && now.isBefore(nextCleanupAt)) {
            return;
        }
        int inspected = 0;
        while (!expiries.isEmpty()
                && !now.isBefore(expiries.peekFirst().expiresAt())
                && inspected < CLEANUP_BATCH_SIZE) {
            var expiry = expiries.removeFirst();
            var entry = entries.get(expiry.key());
            if (entry != null
                    && entry.result.isDone()
                    && entry.completedAt != null
                    && entry.completedAt.plus(ttl).equals(expiry.expiresAt())) {
                entries.remove(expiry.key(), entry);
            }
            inspected++;
        }
        nextCleanupAt = now.plus(CLEANUP_INTERVAL);
    }

    int entryCount() {
        return entries.size();
    }

    private void produce(RequestKey key, Entry<T> entry, Supplier<T> operation) {
        try {
            T value = operation.get();
            synchronized (entries) {
                entry.completedAt = clock.instant();
                expiries.addLast(new EntryExpiry(key, entry.completedAt.plus(ttl)));
                entry.result.complete(value);
            }
        } catch (Throwable failure) {
            entry.result.completeExceptionally(failure);
            entries.remove(key, entry);
        }
    }

    private T await(CompletableFuture<T> result) {
        try {
            return result.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private record RequestKey(String sourceHash, UUID requestToken) {
    }

    private record EntryExpiry(RequestKey key, Instant expiresAt) {
    }

    private static final class Entry<T> {
        private Instant completedAt;
        private final CompletableFuture<T> result = new CompletableFuture<>();

    }
}
