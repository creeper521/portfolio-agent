package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.ClarificationRecoveryTemplate;
import com.portfolio.agent.turn.planning.DiscussionSelectionTemplate;
import com.portfolio.agent.turn.planning.DiscussionClarificationTemplate;

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

/**
 * Short-lived, one-consume clarification authority bound to conversation and token hash.
 *
 * <p>澄清存储：短 TTL、一次性消费的澄清权威。每条记录绑定会话 ID、
 * ResumeToken 哈希与内容发布 ID；reserve/commit 两段式协议保证同一澄清
 * 同时只被一个请求占用。本实现为内存态，仅用于快速测试与定向诊断，
 * 标准开发与生产使用 PostgreSQL State。</p>
 */
public final class ClarificationStore {
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /** 构造澄清存储；TTL 必须为正且不超过 30 分钟。 */
    public ClarificationStore(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("clarification ttl is invalid");
        }
        this.ttl = ttl;
    }

    /** 保存单条澄清记录。 */
    public void save(Record record) {
        saveAllAtomically(List.of(record));
    }

    /**
     * 原子保存一批澄清记录。
     *
     * <p>先统一校验绑定与 ID 唯一性，再统一写入同一过期时间，避免半批写入。</p>
     *
     * @throws IllegalStateException 任一澄清 ID 已存在或批内重复
     */
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

    /**
     * 预留澄清：校验访问权限与答案有效性后，为当前请求原子占位。
     *
     * <p>并发规则：其他请求的有效预留未过期时返回 IN_PROGRESS 与建议重试
     * 秒数。预留过期时间取请求给定值与澄清自身过期时间的较早者。</p>
     *
     * @param requestId 发起本轮 Turn 的请求标识，用于预留归属判定
     */
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

    /**
     * 提交预留：把澄清标记为已消费（一次性）。
     *
     * <p>仅当预留属于该请求、未过期、澄清未消费且答案仍可解析时成功。</p>
     */
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

    /** 无副作用地判断当前请求此刻能否提交该预留。 */
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

    /** 释放指定请求持有的全部未消费预留，返回释放数量。 */
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

    /** 依次校验消费状态、过期、会话与令牌哈希（常数时间比较）、内容发布一致性。 */
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

    /** 清空指定会话的全部澄清记录。 */
    public void clear(String conversationId) {
        entries.entrySet().removeIf(value ->
                value.getValue().record().conversationId().equals(conversationId));
    }

    /** 删除至多 limit 条已过期记录，返回实际删除数量。 */
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

    /**
     * 把会话的存活澄清重新绑定到新的令牌哈希（续期令牌后澄清仍可用）。
     *
     * @throws IllegalStateException 存活澄清数量超过 limit
     */
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

    /** 把访客答案解析为字段级绑定答案；无法唯一解析时返回 null。 */
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

    /** 校验公开挑战与内部绑定一一对应：恰好一个字段、绑定值与字段形状匹配。 */
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

    /** 按恢复模板类型校验绑定键前缀与公开字段形状的匹配关系。 */
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
        if (record.resumeTemplate() instanceof DiscussionClarificationTemplate discussion) {
            List<String> actualValues = record.choiceBindings().values().stream()
                    .flatMap(value -> value.values().stream())
                    .toList();
            java.util.Set<String> actualBindings = actualValues.stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            java.util.Set<String> expectedBindings = discussion.isReenterAllowed()
                    ? java.util.Set.of("discussion:reenter")
                    : discussion.getAllowedFacets().stream()
                    .map(value -> "discussion:facet:" + value.name())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean invalid = !(publicField instanceof ClarificationChallenge.SingleChoiceField)
                    || actualValues.size() != actualBindings.size()
                    || !actualBindings.equals(expectedBindings)
                    || actualBindings.stream()
                    .anyMatch(value -> !validDiscussionBinding(discussion, value));
            if (invalid) {
                throw new IllegalArgumentException(
                        "discussion clarification binding is invalid");
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

    /** 校验讨论澄清绑定（discussion:reenter 或 discussion:facet:X）与模板许可一致。 */
    private boolean validDiscussionBinding(
            DiscussionClarificationTemplate template, String value) {
        if (value.equals("discussion:reenter")) {
            return template.isReenterAllowed();
        }
        String prefix = "discussion:facet:";
        if (!value.startsWith(prefix)) return false;
        try {
            return template.allowsFacet(
                    com.portfolio.agent.turn.planning.UserGoalProposal.Facet.valueOf(
                            value.substring(prefix.length())));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** 校验被阻塞目标各字段的选项绑定键格式。 */
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

    /**
     * 澄清记录：公开挑战 + 内部答案绑定 + 恢复模板，并绑定会话、
     * 令牌哈希与内容发布。
     */
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
    /** 文本字段的内部绑定：绑定键与长度上限（1..2000）。 */
    public record TextBinding(String bindingKey, int limit) {
        public TextBinding {
            bindingKey = ContinuationContext.text(bindingKey, "bindingKey");
            if (limit < 1 || limit > 2000) throw new IllegalArgumentException("limit is invalid");
        }
    }
    /** 访客澄清答案封闭接口：单选 choiceId 或自由文本。 */
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
    /** 解析后的答案：命中的字段 ID、内部绑定键与可选文本。 */
    public record ResolvedAnswer(String fieldId, String bindingKey, String text) { }
    /** 预留结果：状态 + 命中记录 + 解析答案 + 建议重试秒数。 */
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
    /** 预留状态：已预留/他请求进行中/未找到/已过期/已消费/未授权/发布过期/答案无效。 */
    public enum Status {
        RESERVED, IN_PROGRESS, NOT_FOUND, EXPIRED, ALREADY_CONSUMED,
        UNAUTHORIZED, STALE_RELEASE, INVALID_ANSWER
    }
    /** 请求预留：归属的请求 ID 与预留过期时间。 */
    private record Reservation(UUID requestId, Instant expiresAt) { }
    /** 存储条目：记录 + 澄清过期时间 + 消费标记 + 当前预留。 */
    private record Entry(
            Record record, Instant expiresAt, boolean consumed,
            Reservation reservation) { }
}
