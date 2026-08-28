package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics.AdmissionLevel;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics.NormalizationRule;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputCompiler;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftRules;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qwen General v4 Draft Admission：把宽 wire 表达确定性编译为严格
 * {@code general.draft.v3}，不生成、裁剪或猜测正文。
 */
public final class GeneralProviderDraftCompiler implements StructuredOutputCompiler {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "definition", "mechanism", "caveats");
    private static final Set<String> CAVEAT_FIELDS = Set.of("kind", "sentences");
    private static final Set<String> CAVEAT_KINDS =
            java.util.Arrays.stream(GeneralDraftCodec.CaveatKind.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());
    private static final String CLOSING_SUFFIX = "”’」』）》】";
    private static final String TERMINAL_PUNCTUATION = ".。!?！？";

    private final GeneralKnowledgeRequest request;
    private final ModelOutputDiagnostics outputDiagnostics;

    public GeneralProviderDraftCompiler(GeneralKnowledgeRequest request) {
        this(request, ModelOutputDiagnostics.none());
    }

    public GeneralProviderDraftCompiler(
            GeneralKnowledgeRequest request,
            ModelOutputDiagnostics outputDiagnostics) {
        this.request = Objects.requireNonNull(request, "request");
        this.outputDiagnostics = Objects.requireNonNull(
                outputDiagnostics, "outputDiagnostics");
    }

    @Override
    public String profileVersion() {
        return com.portfolio.agent.infrastructure.model.structured.OperationBinding
                .GENERAL_DRAFT_OUTPUT_COMPILER_VERSION;
    }

    @Override
    public JsonNode compile(JsonNode providerDraft) {
        requireObject(providerDraft);
        if (request.getKind() != GeneralKnowledgeRequest.Kind.EXPLANATION) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        AdmissionTracker tracker = new AdmissionTracker();
        int unknownFields = 0;
        java.util.Iterator<String> fields = providerDraft.fieldNames();
        while (fields.hasNext()) {
            if (!ROOT_FIELDS.contains(fields.next())) {
                unknownFields++;
            }
        }
        if (unknownFields > 0) {
            tracker.record(NormalizationRule.UNKNOWN_FIELD_COUNT, unknownFields,
                    AdmissionLevel.NORMALIZED);
        }

        GeneralDraftRules.ExplanationRule rule = GeneralDraftRules.explanation(
                request.getDepth());
        NormalizedRole definition = coreRole(
                providerDraft, "definition", rule.minimumSentencesPerRole(),
                rule.maximumSentencesPerRole(), tracker);
        NormalizedRole mechanism = coreRole(
                providerDraft, "mechanism", rule.minimumSentencesPerRole(),
                rule.maximumSentencesPerRole(), tracker);
        int totalSentences = definition.sentenceCount() + mechanism.sentenceCount();
        if (totalSentences < rule.minimumCanonicalSentences()
                || totalSentences > rule.maximumCanonicalSentences()) {
            throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SENTENCE_COUNT");
        }

        ObjectNode canonical = JSON.objectNode();
        canonical.put("topic", GeneralDraftRules.topic(request));
        ArrayNode statements = canonical.putArray("statements");
        statements.add(statement("DEFINITION", definition.text(), "DEFINITION"));
        statements.add(statement("MECHANISM", mechanism.text(), "MECHANISM"));
        canonical.set("caveats", caveats(providerDraft, tracker));
        tracker.publish(outputDiagnostics);
        return canonical;
    }

    private NormalizedRole coreRole(
            JsonNode root, String field, int minimumSentences,
            int maximumSentences, AdmissionTracker tracker) {
        JsonNode value = root.get(field);
        if (value == null) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        List<String> rawItems = new ArrayList<>();
        int maximumItemCharacters;
        if (value.isTextual()) {
            rawItems.add(value.textValue());
            maximumItemCharacters = 4000;
            tracker.record(NormalizationRule.WRAP_STRING_AS_ARRAY, 1,
                    AdmissionLevel.NORMALIZED);
        } else if (value.isArray()) {
            if (value.isEmpty() || value.size() > 6) {
                throw outsideScope("SEQUENCE_SIZE");
            }
            for (JsonNode item : value) {
                if (!item.isTextual()) {
                    throw outsideScope("SEQUENCE_ITEM_TYPE");
                }
                rawItems.add(item.textValue());
            }
            maximumItemCharacters = 1000;
        } else {
            throw outsideScope("FIELD_TYPE");
        }

        List<String> normalized = new ArrayList<>(rawItems.size());
        int sentenceCount = 0;
        for (String item : rawItems) {
            String text = normalizeText(item, tracker);
            if (text.length() > maximumItemCharacters) {
                throw outsideScope("CHARACTER_LIMIT");
            }
            sentenceCount += countChineseNaturalSentences(text);
            normalized.add(text);
        }
        if (sentenceCount < minimumSentences || sentenceCount > maximumSentences) {
            throw outsideScope("SENTENCE_COUNT");
        }
        if (normalized.size() > 1) {
            tracker.record(NormalizationRule.JOIN_ROLE_SENTENCES,
                    normalized.size() - 1, null);
        }
        String joined = String.join(" ", normalized);
        if (joined.length() > 4000) {
            throw outsideScope("CHARACTER_LIMIT");
        }
        return new NormalizedRole(joined, sentenceCount);
    }

    private ArrayNode caveats(JsonNode root, AdmissionTracker tracker) {
        JsonNode values = root.get("caveats");
        if (values == null || values.isNull()) {
            tracker.record(NormalizationRule.MISSING_CAVEATS_AS_EMPTY, 1,
                    AdmissionLevel.DEGRADED);
            return JSON.arrayNode();
        }
        try {
            if (!values.isArray() || values.size() > 10) {
                throw outsideScope("OPTIONAL_CAVEATS");
            }
            ArrayNode result = JSON.arrayNode();
            for (JsonNode value : values) {
                requireObject(value);
                if (!hasExactly(value, CAVEAT_FIELDS)) {
                    throw outsideScope("OPTIONAL_CAVEATS");
                }
                JsonNode kind = value.get("kind");
                if (!kind.isTextual() || !CAVEAT_KINDS.contains(kind.textValue())) {
                    throw outsideScope("OPTIONAL_CAVEATS");
                }
                NormalizedRole sentences = optionalSentences(
                        value.get("sentences"), tracker);
                ObjectNode caveat = JSON.objectNode();
                caveat.put("kind", kind.textValue());
                caveat.put("text", sentences.text());
                result.add(caveat);
            }
            return result;
        } catch (StructuredOutputValidationException invalidOptional) {
            tracker.record(NormalizationRule.DROPPED_INVALID_OPTIONAL_CAVEATS, 1,
                    AdmissionLevel.DEGRADED);
            return JSON.arrayNode();
        }
    }

    private NormalizedRole optionalSentences(
            JsonNode value, AdmissionTracker tracker) {
        if (value == null) {
            throw outsideScope("OPTIONAL_CAVEATS");
        }
        List<String> rawItems = new ArrayList<>();
        if (value.isTextual()) {
            rawItems.add(value.textValue());
            tracker.record(NormalizationRule.WRAP_STRING_AS_ARRAY, 1,
                    AdmissionLevel.NORMALIZED);
        } else if (value.isArray()) {
            if (value.isEmpty() || value.size() > 2) {
                throw outsideScope("OPTIONAL_CAVEATS");
            }
            for (JsonNode item : value) {
                if (!item.isTextual()) {
                    throw outsideScope("OPTIONAL_CAVEATS");
                }
                rawItems.add(item.textValue());
            }
        } else {
            throw outsideScope("OPTIONAL_CAVEATS");
        }
        List<String> normalized = new ArrayList<>(rawItems.size());
        int sentenceCount = 0;
        for (String item : rawItems) {
            String text = normalizeText(item, tracker);
            if (text.length() > 1000) {
                throw outsideScope("OPTIONAL_CAVEATS");
            }
            sentenceCount += countChineseNaturalSentences(text);
            normalized.add(text);
        }
        if (sentenceCount < 1 || sentenceCount > 2) {
            throw outsideScope("OPTIONAL_CAVEATS");
        }
        if (normalized.size() > 1) {
            tracker.record(NormalizationRule.JOIN_ROLE_SENTENCES,
                    normalized.size() - 1, null);
        }
        String joined = String.join(" ", normalized);
        if (joined.length() > 1000) {
            throw outsideScope("OPTIONAL_CAVEATS");
        }
        return new NormalizedRole(joined, sentenceCount);
    }

    private ObjectNode statement(String role, String text, String aspect) {
        ObjectNode statement = JSON.objectNode();
        statement.put("role", role);
        statement.put("text", text);
        statement.putNull("subject");
        statement.putNull("dimension");
        statement.putArray("aspects").add(aspect);
        return statement;
    }

    static String normalizeText(String raw) {
        return normalizeText(raw, new AdmissionTracker());
    }

    private static String normalizeText(
            String raw, AdmissionTracker tracker) {
        if (raw == null) {
            throw outsideScope("SEQUENCE_ITEM_TYPE");
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC);
        if (!value.equals(raw)) {
            tracker.record(NormalizationRule.UNICODE_NORMALIZE_NFC, 1,
                    AdmissionLevel.NORMALIZED);
        }
        String trimmed = trimWhitespace(value);
        if (!trimmed.equals(value)) {
            tracker.record(NormalizationRule.TRIM_TEXT, 1,
                    AdmissionLevel.NORMALIZED);
        }
        String collapsed = collapseWhitespace(trimmed);
        if (!collapsed.equals(trimmed)) {
            tracker.record(NormalizationRule.COLLAPSE_MEANINGLESS_WHITESPACE, 1,
                    AdmissionLevel.NORMALIZED);
        }
        String punctuated = normalizeTerminalPunctuation(collapsed);
        if (!punctuated.equals(collapsed)) {
            tracker.record(NormalizationRule.NORMALIZE_TERMINAL_PUNCTUATION, 1,
                    AdmissionLevel.NORMALIZED);
        }
        return punctuated;
    }

    private static String collapseWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            boolean whitespace = isWhitespace(codePoint);
            if (whitespace) {
                if (!previousWhitespace) {
                    result.append(' ');
                }
            } else {
                result.appendCodePoint(codePoint);
            }
            previousWhitespace = whitespace;
        }
        return result.toString();
    }

    private static String trimWhitespace(String value) {
        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (!isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        int end = value.length();
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }

    private static String normalizeTerminalPunctuation(String value) {
        int suffixStart = value.length();
        while (suffixStart > 0) {
            int codePoint = value.codePointBefore(suffixStart);
            if (CLOSING_SUFFIX.indexOf(codePoint) < 0) {
                break;
            }
            suffixStart -= Character.charCount(codePoint);
        }
        String body = value.substring(0, suffixStart);
        String suffix = value.substring(suffixStart);
        if (body.isEmpty()) {
            throw outsideScope("EMPTY_TEXT");
        }
        int last = body.codePointBefore(body.length());
        int lastStart = body.length() - Character.charCount(last);
        if (TERMINAL_PUNCTUATION.indexOf(last) >= 0) {
            body = body.substring(0, lastStart) + '。';
        } else {
            body += '。';
        }
        return body + suffix;
    }

    private static int countChineseNaturalSentences(String value) {
        int count = 0;
        int segmentStart = 0;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            int next = index + Character.charCount(codePoint);
            if ("。！？!?".indexOf(codePoint) >= 0) {
                String segment = value.substring(segmentStart, index);
                if (!GeneralDraftRules.isChineseDominant(segment)) {
                    throw outsideScope("LANGUAGE");
                }
                count++;
                segmentStart = next;
            }
            index = next;
        }
        if (count < 1 || !onlyClosingSuffix(value.substring(segmentStart))) {
            throw outsideScope("SENTENCE_BOUNDARY");
        }
        return count;
    }

    private static boolean onlyClosingSuffix(String value) {
        return value.codePoints().allMatch(codePoint ->
                CLOSING_SUFFIX.indexOf(codePoint) >= 0
                        || Character.isWhitespace(codePoint));
    }

    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
    }

    private static boolean hasExactly(JsonNode node, Set<String> fields) {
        java.util.HashSet<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual.equals(fields);
    }

    private static StructuredOutputValidationException outsideScope(String suffix) {
        return fail(StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_" + suffix);
    }

    private static StructuredOutputValidationException fail(
            StructuredOutputValidationException.Reason reason) {
        return new StructuredOutputValidationException(
                reason, reason.name(),
                StructuredOutputValidationException.Stage.DETERMINISTIC_COMPILER);
    }

    private static StructuredOutputValidationException fail(
            StructuredOutputValidationException.Reason reason,
            String diagnosticReason) {
        return new StructuredOutputValidationException(
                reason, diagnosticReason,
                StructuredOutputValidationException.Stage.DETERMINISTIC_COMPILER);
    }

    private record NormalizedRole(String text, int sentenceCount) { }

    private static final class AdmissionTracker {
        private final Map<NormalizationRule, Integer> counts =
                new LinkedHashMap<>();
        private AdmissionLevel level = AdmissionLevel.EXACT;

        private void record(
                NormalizationRule rule, int count,
                AdmissionLevel resultingLevel) {
            counts.merge(rule, count, Integer::sum);
            if (resultingLevel == AdmissionLevel.DEGRADED
                    || resultingLevel == AdmissionLevel.NORMALIZED
                    && level == AdmissionLevel.EXACT) {
                level = resultingLevel;
            }
        }

        private void publish(ModelOutputDiagnostics diagnostics) {
            if (counts.isEmpty()) {
                diagnostics.admitted("GENERAL_KNOWLEDGE", level, null, 1);
                return;
            }
            for (Map.Entry<NormalizationRule, Integer> entry : counts.entrySet()) {
                diagnostics.admitted("GENERAL_KNOWLEDGE", level,
                        entry.getKey(), entry.getValue());
            }
        }
    }
}
