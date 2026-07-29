package com.portfolio.agent.answer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AnswerIdempotencyCoordinator<T> {

    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<RequestKey, Entry<T>> entries = new ConcurrentHashMap<>();

    public AnswerIdempotencyCoordinator(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    public T execute(String sourceHash, UUID requestToken, Supplier<T> operation) {
        var key = new RequestKey(
                Objects.requireNonNull(sourceHash, "sourceHash must not be null"),
                Objects.requireNonNull(requestToken, "requestToken must not be null")
        );
        Objects.requireNonNull(operation, "operation must not be null");

        while (true) {
            var now = clock.instant();
            var candidate = new Entry<T>(now);
            var existing = entries.putIfAbsent(key, candidate);
            var selected = existing == null ? candidate : existing;

            if (existing != null && selected.isExpired(now, ttl)) {
                entries.remove(key, selected);
                continue;
            }
            if (existing == null) {
                produce(key, candidate, operation);
            }
            return await(selected.result);
        }
    }

    private void produce(RequestKey key, Entry<T> entry, Supplier<T> operation) {
        try {
            entry.result.complete(operation.get());
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

    private static final class Entry<T> {
        private final Instant createdAt;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        private Entry(Instant createdAt) {
            this.createdAt = createdAt;
        }

        private boolean isExpired(Instant now, Duration ttl) {
            return result.isDone() && !now.isBefore(createdAt.plus(ttl));
        }
    }
}
