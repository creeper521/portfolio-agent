package com.portfolio.agent.turn.continuation;

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
        Objects.requireNonNull(record, "record");
        validateBindings(record);
        Entry entry = new Entry(record, clock.instant().plus(ttl), false);
        if (entries.putIfAbsent(record.challenge().getClarificationId(), entry) != null) {
            throw new IllegalStateException("clarification id already exists");
        }
    }

    public ConsumeResult consume(
            String clarificationId, String conversationId, byte[] resumeTokenHash,
            String currentContentReleaseId, ClarificationAnswer answer) {
        AtomicReference<ConsumeResult> result = new AtomicReference<>(ConsumeResult.of(Status.NOT_FOUND));
        entries.computeIfPresent(clarificationId, (key, entry) -> {
            if (entry.consumed()) {
                result.set(ConsumeResult.of(Status.ALREADY_CONSUMED));
                return entry;
            }
            if (!clock.instant().isBefore(entry.expiresAt())) {
                result.set(ConsumeResult.of(Status.EXPIRED));
                return entry;
            }
            if (!entry.record().conversationId().equals(conversationId)
                    || !MessageDigest.isEqual(entry.record().resumeTokenHash(), resumeTokenHash)) {
                result.set(ConsumeResult.of(Status.UNAUTHORIZED));
                return entry;
            }
            if (!entry.record().contentReleaseId().equals(currentContentReleaseId)) {
                result.set(ConsumeResult.of(Status.STALE_RELEASE));
                return entry;
            }
            ResolvedAnswer resolved = resolve(entry.record(), answer);
            if (resolved == null) {
                result.set(ConsumeResult.of(Status.INVALID_ANSWER));
                return entry;
            }
            result.set(new ConsumeResult(Status.CONSUMED, entry.record(), resolved));
            return new Entry(entry.record(), entry.expiresAt(), true);
        });
        return result.get();
    }

    public void clear(String conversationId) {
        entries.entrySet().removeIf(value ->
                value.getValue().record().conversationId().equals(conversationId));
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
    }

    public record Record(
            String conversationId, byte[] resumeTokenHash, String contentReleaseId,
            ClarificationChallenge challenge,
            Map<String, Map<String, String>> choiceBindings,
            Map<String, TextBinding> textBindings) {
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
    public record ConsumeResult(Status status, Record record, ResolvedAnswer answer) {
        public ConsumeResult { Objects.requireNonNull(status, "status"); }
        public static ConsumeResult of(Status status) { return new ConsumeResult(status, null, null); }
    }
    public enum Status {
        CONSUMED, NOT_FOUND, EXPIRED, ALREADY_CONSUMED,
        UNAUTHORIZED, STALE_RELEASE, INVALID_ANSWER
    }
    private record Entry(Record record, Instant expiresAt, boolean consumed) { }
}
