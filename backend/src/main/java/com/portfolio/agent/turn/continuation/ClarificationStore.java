package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.ClarificationRecoveryTemplate;
import com.portfolio.agent.turn.planning.DiscussionSelectionTemplate;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

/** Short-lived, one-consume clarification authority bound to conversation and token hash. */
public final class ClarificationStore {
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public ClarificationStore(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("clarification ttl is invalid");
        }
        this.ttl = ttl;
    }

    public void save(Record record) {
        saveAllAtomically(List.of(record));
    }

    public synchronized void saveAllAtomically(List<Record> records) {
        List<Record> copied = List.copyOf(records);
        java.util.HashSet<String> incomingIds = new java.util.HashSet<>();
        copied.forEach(record -> {
            Objects.requireNonNull(record, "record");
            validateBindings(record);
            String clarificationId = record.challenge().getClarificationId();
            if (!incomingIds.add(clarificationId) || entries.containsKey(clarificationId)) {
                throw new IllegalStateException("clarification id already exists");
            }
        });
        Instant expiresAt = clock.instant().plus(ttl);
        copied.forEach(record -> entries.put(
                record.challenge().getClarificationId(),
                new Entry(record, expiresAt, false, null)));
    }

    public ReserveResult reserve(
            String clarificationId, String conversationId,
            byte[] resumeTokenHash, String currentContentReleaseId,
            ClarificationAnswer answer, UUID requestId,
            Instant reservationExpiresAt) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        Instant now = clock.instant();
        AtomicReference<ReserveResult> result = new AtomicReference<>(
                ReserveResult.of(Status.NOT_FOUND));
        entries.computeIfPresent(clarificationId, (key, entry) -> {
            Status invalid = validateAccess(
                    entry, conversationId, resumeTokenHash,
                    currentContentReleaseId, now);
            if (invalid != null) {
                result.set(ReserveResult.of(invalid));
                return entry;
            }
            Reservation current = entry.reservation();
            if (current != null && !current.requestId().equals(requestId)
                    && now.isBefore(current.expiresAt())) {
                long retryAfter = Math.max(1L,
                        Duration.between(now, current.expiresAt()).toSeconds());
                result.set(ReserveResult.inProgress(retryAfter));
                return entry;
            }
            ResolvedAnswer resolved = resolve(entry.record(), answer);
            if (resolved == null) {
                result.set(ReserveResult.of(Status.INVALID_ANSWER));
                return entry;
            }
            Instant boundedExpiry = reservationExpiresAt.isBefore(entry.expiresAt())
                    ? reservationExpiresAt : entry.expiresAt();
            if (!now.isBefore(boundedExpiry)) {
                result.set(ReserveResult.of(Status.EXPIRED));
                return entry;
            }
            result.set(new ReserveResult(
                    Status.RESERVED, entry.record(), resolved, 0));
            return new Entry(
                    entry.record(), entry.expiresAt(), false,
                    new Reservation(requestId, boundedExpiry));
        });
        return result.get();
    }

    public synchronized boolean commitReservation(
            String clarificationId, UUID requestId,
            ClarificationAnswer answer, Instant completedAt) {
        Entry entry = entries.get(clarificationId);
        if (entry == null || entry.consumed()
                || entry.reservation() == null
                || !entry.reservation().requestId().equals(requestId)
                || !completedAt.isBefore(entry.reservation().expiresAt())
                || resolve(entry.record(), answer) == null) {
            return false;
        }
        entries.put(clarificationId, new Entry(
                entry.record(), entry.expiresAt(), true, null));
        return true;
    }

    public synchronized boolean canCommitReservation(
            String clarificationId, UUID requestId,
            ClarificationAnswer answer, Instant completedAt) {
        Entry entry = entries.get(clarificationId);
        return entry != null && !entry.consumed()
                && entry.reservation() != null
                && entry.reservation().requestId().equals(requestId)
                && completedAt.isBefore(entry.reservation().expiresAt())
                && resolve(entry.record(), answer) != null;
    }

    public synchronized int releaseReservations(UUID requestId) {
        int released = 0;
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            Entry entry = item.getValue();
            if (!entry.consumed() && entry.reservation() != null
                    && entry.reservation().requestId().equals(requestId)) {
                entries.put(item.getKey(), new Entry(
                        entry.record(), entry.expiresAt(), false, null));
                released++;
            }
        }
        return released;
    }

    private Status validateAccess(
            Entry entry, String conversationId, byte[] resumeTokenHash,
            String currentContentReleaseId, Instant now) {
        if (entry.consumed()) return Status.ALREADY_CONSUMED;
        if (!now.isBefore(entry.expiresAt())) return Status.EXPIRED;
        if (!entry.record().conversationId().equals(conversationId)
                || !MessageDigest.isEqual(
                entry.record().resumeTokenHash(), resumeTokenHash)) {
            return Status.UNAUTHORIZED;
        }
        if (!entry.record().contentReleaseId()
                .equals(currentContentReleaseId)) {
            return Status.STALE_RELEASE;
        }
        return null;
    }

    public void clear(String conversationId) {
        entries.entrySet().removeIf(value ->
                value.getValue().record().conversationId().equals(conversationId));
    }

    public int cleanup(Instant now, int limit) {
        if (limit < 1) return 0;
        int removed = 0;
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            if (removed >= limit) break;
            if (!now.isBefore(entry.getValue().expiresAt())
                    && entries.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    public synchronized int rebindLiveChallenges(
            String conversationId, byte[] newTokenHash, Instant now, int limit) {
        List<Map.Entry<String, Entry>> live = entries.entrySet().stream()
                .filter(entry -> entry.getValue().record().conversationId().equals(conversationId))
                .filter(entry -> !entry.getValue().consumed())
                .filter(entry -> now.isBefore(entry.getValue().expiresAt()))
                .toList();
        if (live.size() > limit) {
            throw new IllegalStateException("live clarification rebind limit exceeded");
        }
        for (Map.Entry<String, Entry> item : live) {
            Record current = item.getValue().record();
            Record rebound = new Record(
                    current.conversationId(), newTokenHash, current.contentReleaseId(),
                    current.challenge(), current.choiceBindings(), current.textBindings(),
                    current.resumeTemplate());
            validateBindings(rebound);
            entries.put(item.getKey(), new Entry(
                    rebound, item.getValue().expiresAt(), false,
                    item.getValue().reservation()));
        }
        return live.size();
    }

    private ResolvedAnswer resolve(Record record, ClarificationAnswer answer) {
        if (answer instanceof ClarificationAnswer.Choice choice) {
            List<Map.Entry<String, Map<String, String>>> matches = record.choiceBindings().entrySet()
                    .stream().filter(value -> value.getValue().containsKey(choice.choiceId())).toList();
            if (matches.size() != 1) return null;
            String binding = matches.getFirst().getValue().get(choice.choiceId());
            return new ResolvedAnswer(matches.getFirst().getKey(), binding, null);
        }
        if (answer instanceof ClarificationAnswer.Text text) {
            if (record.textBindings().size() != 1) return null;
            Map.Entry<String, TextBinding> match = record.textBindings().entrySet().iterator().next();
            TextBinding binding = match.getValue();
            if (binding == null || text.text().length() > binding.limit()) return null;
            return new ResolvedAnswer(match.getKey(), binding.bindingKey(), text.text().trim());
        }
        return null;
    }

    private void validateBindings(Record record) {
        Map<String, ClarificationChallenge.Field> fields = new LinkedHashMap<>();
        record.challenge().getFields().forEach(field -> fields.put(field.getFieldId(), field));
        if (fields.size() != record.challenge().getFields().size()) {
            throw new IllegalArgumentException("clarification field ids must be unique");
        }
        if (fields.size() != 1) {
            throw new IllegalArgumentException("clarification challenge must contain exactly one field");
        }
        for (Map.Entry<String, Map<String, String>> entry : record.choiceBindings().entrySet()) {
            ClarificationChallenge.Field field = fields.get(entry.getKey());
            if (!(field instanceof ClarificationChallenge.SingleChoiceField choice)
                    || choice.getChoices().stream().anyMatch(value ->
                    !entry.getValue().containsKey(value.choiceId()))
                    || entry.getValue().size() != choice.getChoices().size()) {
                throw new IllegalArgumentException("choice bindings do not match public challenge");
            }
        }
        for (Map.Entry<String, TextBinding> entry : record.textBindings().entrySet()) {
            ClarificationChallenge.Field field = fields.get(entry.getKey());
            if (!(field instanceof ClarificationChallenge.TextField text)
                    || text.getLimit() != entry.getValue().limit()) {
                throw new IllegalArgumentException("text bindings do not match public challenge");
            }
        }
        if (record.choiceBindings().size() + record.textBindings().size() != fields.size()) {
            throw new IllegalArgumentException("every challenge field requires an internal binding");
        }
        validateBlockedFieldBinding(record, fields.values().iterator().next());
    }

    private void validateBlockedFieldBinding(
            Record record, ClarificationChallenge.Field publicField) {
        if (record.resumeTemplate() instanceof DiscussionSelectionTemplate selection) {
            boolean invalid = !(publicField instanceof ClarificationChallenge.SingleChoiceField)
                    || record.choiceBindings().values().stream()
                    .flatMap(value -> value.values().stream())
                    .anyMatch(value -> !value.startsWith("result-item:")
                            || !selection.allows(value.substring("result-item:".length())));
            if (invalid) {
                throw new IllegalArgumentException("discussion selection binding is invalid");
            }
            return;
        }
        BlockedGoalTemplate goal = (BlockedGoalTemplate) record.resumeTemplate();
        com.portfolio.agent.turn.planning.ClarificationProposal.Field blockedField =
                goal.getUnresolvedField();
        String requiredPrefix = switch (blockedField) {
            case SUBJECT -> "subject:";
            case OUTPUT -> "output:";
            case REQUESTED_SIZE -> "size:";
            case CONSTRAINT -> throw new IllegalArgumentException("constraint clarification is not supported");
            case GOAL -> throw new IllegalArgumentException("goal clarification is not supported");
        };
        boolean choice = publicField instanceof ClarificationChallenge.SingleChoiceField;
        boolean fieldKindAllowed = choice
                || blockedField == com.portfolio.agent.turn.planning.ClarificationProposal.Field.SUBJECT
                && publicField instanceof ClarificationChallenge.TextField;
        if (!fieldKindAllowed) {
            throw new IllegalArgumentException("public field kind does not match blocked goal");
        }
        if (choice) {
            boolean mismatched = record.choiceBindings().values().stream()
                    .flatMap(value -> value.values().stream())
                    .anyMatch(value -> !validChoiceBinding(blockedField, value));
            if (mismatched) throw new IllegalArgumentException("choice binding does not match blocked goal");
        } else {
            boolean mismatched = record.textBindings().values().stream()
                    .anyMatch(value -> !value.bindingKey().equals(requiredPrefix + "text"));
            if (mismatched) throw new IllegalArgumentException("text binding does not match blocked goal");
        }
    }

    private boolean validChoiceBinding(
            com.portfolio.agent.turn.planning.ClarificationProposal.Field field,
            String value) {
        return switch (field) {
            case SUBJECT -> value.matches("subject:(?:PROJECT|CASE):[A-Za-z0-9._-]{1,128}");
            case OUTPUT -> value.matches("output:(?:OVERVIEW|BACKGROUND|RESPONSIBILITY|SOLUTION|VERIFICATION|STATUS|COMPARISON|RECOMMENDATION)");
            case REQUESTED_SIZE -> value.matches("size:[1-5]");
            case GOAL, CONSTRAINT -> false;
        };
    }

    public record Record(
            String conversationId, byte[] resumeTokenHash, String contentReleaseId,
            ClarificationChallenge challenge,
            Map<String, Map<String, String>> choiceBindings,
            Map<String, TextBinding> textBindings,
            ClarificationRecoveryTemplate resumeTemplate) {
        public Record {
            conversationId = ContinuationContext.text(conversationId, "conversationId");
            resumeTokenHash = Objects.requireNonNull(resumeTokenHash, "resumeTokenHash").clone();
            contentReleaseId = ContinuationContext.text(contentReleaseId, "contentReleaseId");
            Objects.requireNonNull(challenge, "challenge");
            LinkedHashMap<String, Map<String, String>> copiedChoices = new LinkedHashMap<>();
            Objects.requireNonNull(choiceBindings, "choiceBindings").forEach((field, choices) ->
                    copiedChoices.put(field, Map.copyOf(choices)));
            choiceBindings = Map.copyOf(copiedChoices);
            textBindings = Map.copyOf(Objects.requireNonNull(textBindings, "textBindings"));
            Objects.requireNonNull(resumeTemplate, "resumeTemplate");
        }
        @Override public byte[] resumeTokenHash() { return resumeTokenHash.clone(); }
    }
    public record TextBinding(String bindingKey, int limit) {
        public TextBinding {
            bindingKey = ContinuationContext.text(bindingKey, "bindingKey");
            if (limit < 1 || limit > 2000) throw new IllegalArgumentException("limit is invalid");
        }
    }
    public sealed interface ClarificationAnswer
            permits ClarificationAnswer.Choice, ClarificationAnswer.Text {
        record Choice(String choiceId) implements ClarificationAnswer {
            public Choice {
                choiceId = ContinuationContext.text(choiceId, "choiceId");
            }
        }
        record Text(String text) implements ClarificationAnswer {
            public Text {
                text = ContinuationContext.text(text, "text");
            }
        }
    }
    public record ResolvedAnswer(String fieldId, String bindingKey, String text) { }
    public record ReserveResult(
            Status status, Record record, ResolvedAnswer answer,
            long retryAfterSeconds) {
        public ReserveResult { Objects.requireNonNull(status, "status"); }
        public static ReserveResult of(Status status) {
            return new ReserveResult(status, null, null, 0);
        }
        public static ReserveResult inProgress(long retryAfterSeconds) {
            return new ReserveResult(
                    Status.IN_PROGRESS, null, null,
                    Math.max(1L, retryAfterSeconds));
        }
    }
    public enum Status {
        RESERVED, IN_PROGRESS, NOT_FOUND, EXPIRED, ALREADY_CONSUMED,
        UNAUTHORIZED, STALE_RELEASE, INVALID_ANSWER
    }
    private record Reservation(UUID requestId, Instant expiresAt) { }
    private record Entry(
            Record record, Instant expiresAt, boolean consumed,
            Reservation reservation) { }
}
