package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputCompiler;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 把 Provider 仅负责撰写的 General Draft 确定性编译为 general.draft.v2。
 *
 * <p>主题、陈述角色以及对比的 subject/dimension 均来自可信请求；Provider
 * 只提供正文、说明侧面与 caveat。编译器不生成正文、不默认缺失集合，也不做
 * repair、重试或跨模型降级。</p>
 */
public final class GeneralProviderDraftCompiler implements StructuredOutputCompiler {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "kind", "depth", "definitionSentences", "mechanismSentences",
            "comparisonSentences", "caveats");
    private static final Set<String> CAVEAT_FIELDS = Set.of("kind", "sentences");
    private static final Set<String> CAVEAT_KINDS = Set.of(
            "APPLICABILITY_BOUNDARY", "RISK", "EXCEPTION");
    private static final Set<String> COMPARISON_ITEM_FIELDS = Set.of(
            "text", "dimension", "subjectIndex");

    private final GeneralKnowledgeRequest request;

    public GeneralProviderDraftCompiler(GeneralKnowledgeRequest request) {
        this.request = Objects.requireNonNull(request, "request");
    }

    @Override
    public String profileVersion() {
        return com.portfolio.agent.infrastructure.model.structured.OperationBinding
                .GENERAL_DRAFT_OUTPUT_COMPILER_VERSION;
    }

    @Override
    public JsonNode compile(JsonNode draft) {
        requireObject(draft);
        requireOnly(draft, ROOT_FIELDS);
        requireField(draft, "kind");
        requireField(draft, "caveats");
        String kind = text(draft, "kind", 64);
        boolean explanationRequest = request.getKind()
                == GeneralKnowledgeRequest.Kind.EXPLANATION;
        if (explanationRequest != "EXPLANATION".equals(kind)) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        return switch (kind) {
            case "EXPLANATION" -> explanation(draft);
            case "COMPARISON" -> comparison(draft);
            default -> throw fail(
                    StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        };
    }

    private JsonNode explanation(JsonNode draft) {
        requireExact(draft,
                Set.of("kind", "depth", "definitionSentences",
                        "mechanismSentences", "caveats"));
        String depth = text(draft, "depth", 16);
        if (!request.getDepth().name().equals(depth)) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        }
        // 上游 provider 契约已把每数组句数钉为精确值；此处同一常量既作下限也作
        // 上限，保证任何绕过 Schema 的直连路径得到同样的确定性拒绝。
        int requiredSentences = switch (request.getDepth()) {
            case CONCISE -> 1;
            case STANDARD -> 2;
            case DETAILED -> 4;
        };
        ObjectNode canonical = root(request.getTopic());
        ArrayNode statements = canonical.putArray("statements");
        statements.add(statement(
                "DEFINITION",
                sentenceText(draft, "definitionSentences",
                        requiredSentences, requiredSentences, 4000),
                definitionAspects()));
        statements.add(statement(
                "MECHANISM",
                sentenceText(draft, "mechanismSentences",
                        requiredSentences, requiredSentences, 4000),
                mechanismAspects()));
        canonical.set("caveats", caveats(draft));
        return canonical;
    }

    private JsonNode comparison(JsonNode draft) {
        requireExact(draft, Set.of("kind", "comparisonSentences", "caveats"));
        JsonNode values = array(draft, "comparisonSentences");
        List<String> subjects = request.getSubjects();
        List<String> dimensions = orderedDimensions();
        int expected = Math.multiplyExact(subjects.size(), dimensions.size());
        if (expected == 0 || expected > 20) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        if (values.size() < expected) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        if (values.size() > expected) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        }
        Map<String, String> claims = new HashMap<>();
        for (JsonNode value : values) {
            requireObject(value);
            requireOnly(value, COMPARISON_ITEM_FIELDS);
            String text = rawComparisonText(value.get("text"));
            String dimension = declaredDimension(value.get("dimension"), dimensions);
            int subjectIndex = boundedInteger(
                    value.get("subjectIndex"), 1, subjects.size());
            String pairKey = subjectIndex + "\u0000" + dimension;
            if (claims.putIfAbsent(pairKey, text) != null) {
                throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
            }
        }
        ObjectNode canonical = root(String.join(" vs ", request.getSubjects()));
        ArrayNode statements = canonical.putArray("statements");
        for (int subjectIndex = 1; subjectIndex <= subjects.size();
                subjectIndex++) {
            String subject = subjects.get(subjectIndex - 1);
            for (String dimension : dimensions) {
                String text = claims.get(subjectIndex + "\u0000" + dimension);
                if (text == null) {
                    throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_REQUIRED_FIELD_MISSING);
                }
                ObjectNode statement = JSON.objectNode();
                statement.put("role", "COMPARISON");
                statement.put("text", canonicalComparisonText(text, 4000));
                statement.put("subject", subject);
                statement.put("dimension", dimension);
                statement.putArray("aspects");
                statements.add(statement);
            }
        }
        canonical.set("caveats", caveats(draft));
        return canonical;
    }

    private String rawComparisonText(JsonNode value) {
        if (value == null || !value.isTextual()) {
            throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SEQUENCE_ITEM_TYPE");
        }
        String text = value.textValue().trim();
        if (text.isBlank() || text.length() > 4000
                || text.matches(".*[.!?！？].*")
                || !text.matches(".*\\p{IsHan}.*")) {
            throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SENTENCE");
        }
        return text;
    }

    private String declaredDimension(JsonNode value, List<String> dimensions) {
        if (value == null || !value.isTextual()
                || !dimensions.contains(value.textValue())) {
            throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_DIMENSION");
        }
        return value.textValue();
    }

    private int boundedInteger(JsonNode value, int minimum, int maximum) {
        if (value == null || !value.isIntegralNumber()
                || value.intValue() < minimum || value.intValue() > maximum) {
            throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SUBJECT_INDEX");
        }
        return value.intValue();
    }

    private java.util.List<String> orderedDimensions() {
        return request.getDimensions().stream().sorted().toList();
    }

    private ObjectNode root(String topic) {
        ObjectNode root = JSON.objectNode();
        root.put("topic", topic);
        return root;
    }

    private ObjectNode statement(String role, String text, List<String> aspects) {
        ObjectNode statement = JSON.objectNode();
        statement.put("role", role);
        statement.put("text", text);
        ArrayNode aspectValues = statement.putArray("aspects");
        aspects.forEach(aspectValues::add);
        return statement;
    }

    private List<String> definitionAspects() {
        return switch (request.getDepth()) {
            case CONCISE -> List.of("DEFINITION");
            case STANDARD -> List.of("DEFINITION", "TYPICAL_USAGE");
            case DETAILED -> List.of(
                    "DEFINITION", "TYPICAL_USAGE", "COMMON_MISCONCEPTION");
        };
    }

    private List<String> mechanismAspects() {
        return switch (request.getDepth()) {
            case CONCISE -> List.of("MECHANISM");
            case STANDARD -> List.of("MECHANISM", "APPLICABILITY_BOUNDARY");
            case DETAILED -> List.of(
                    "MECHANISM", "APPLICABILITY_BOUNDARY", "TRADE_OFF",
                    "BOUNDARY_CONDITION");
        };
    }

    private ArrayNode caveats(JsonNode draft) {
        JsonNode values = array(draft, "caveats");
        if (values.size() > 10) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        ArrayNode result = JSON.arrayNode();
        for (JsonNode value : values) {
            requireObject(value);
            requireExact(value, CAVEAT_FIELDS);
            ObjectNode caveat = JSON.objectNode();
            caveat.put("kind", closedText(value, "kind", CAVEAT_KINDS));
            caveat.put("text", sentenceText(value, "sentences", 1, 2, 2000));
            result.add(caveat);
        }
        return result;
    }

    private String sentenceText(
            JsonNode node, String field, int minimum, int maximum,
            int maximumCharacters) {
        List<String> values = sentenceValues(node, field);
        if (values.size() < minimum || values.size() > maximum) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SENTENCE_COUNT");
        }
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            appendSentence(result, value, maximumCharacters);
        }
        return result.toString();
    }

    private String sentenceText(List<String> values, int index, int maximumCharacters) {
        if (index < 0 || index >= values.size()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        StringBuilder result = new StringBuilder();
        appendSentence(result, values.get(index), maximumCharacters);
        return result.toString();
    }

    private String canonicalComparisonText(String raw, int maximumCharacters) {
        String text = raw.trim();
        int finalStop = text.lastIndexOf('。');
        if (finalStop >= 0 && finalStop < text.length() - 1) {
            String suffix = text.substring(finalStop + 1);
            if (isClosingSuffixSequence(suffix)) {
                text = text.substring(0, finalStop) + suffix + '。';
            }
        }
        if (!text.endsWith("。")) {
            text += '。';
        }
        if (text.length() > maximumCharacters) {
            throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_CHARACTER_LIMIT");
        }
        return text;
    }

    private List<String> sentenceValues(JsonNode node, String field) {
        requireField(node, field);
        JsonNode value = node.get(field);
        if (value.isArray()) {
            List<String> sentences = new ArrayList<>();
            for (JsonNode item : value) {
                if (!item.isTextual()) {
                    throw fail(StructuredOutputValidationException.Reason
                            .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                            "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SEQUENCE_ITEM_TYPE");
                }
                sentences.addAll(parseSentenceSequence(item.textValue()));
            }
            bindTechnicalLabels(sentences);
            return List.copyOf(sentences);
        }
        if (!value.isTextual()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        List<String> sentences = new ArrayList<>(
                parseSentenceSequence(value.textValue()));
        bindTechnicalLabels(sentences);
        return List.copyOf(sentences);
    }

    private List<String> parseSentenceSequence(String raw) {
        String sequence = raw == null ? "" : raw.trim();
        if (sequence.isBlank() || sequence.matches(".*[.!?！？；;].*")) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SEQUENCE_FORMAT");
        }
        String[] parts = sequence.split("。", -1);
        int length = parts.length;
        if (sequence.endsWith("。")) {
            length--;
        }
        if (length < 1) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SEQUENCE_FORMAT");
        }
        List<String> sentences = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            String sentence = parts[index].trim();
            if (sentence.isBlank()) {
                throw fail(StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                        "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_EMPTY_SEGMENT");
            }
            sentences.add(sentence);
        }
        return List.copyOf(sentences);
    }

    private void bindTechnicalLabels(List<String> sentences) {
        for (int index = 0; index < sentences.size(); index++) {
            String value = sentences.get(index);
            if (value.matches(".*\\p{IsHan}.*")) {
                continue;
            }
            if (isClosingSuffixSequence(value) && index > 0) {
                sentences.set(index - 1, sentences.get(index - 1) + value);
                sentences.remove(index);
                index--;
                continue;
            }
            String rejection = technicalLabelRejection(value);
            if (rejection != null) {
                throw fail(StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                        rejection);
            }
            if (index + 1 >= sentences.size()) {
                throw fail(StructuredOutputValidationException.Reason
                                .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                        "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_TRAILING");
            }
            if (!sentences.get(index + 1).matches(".*\\p{IsHan}.*")) {
                throw fail(StructuredOutputValidationException.Reason
                                .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                        "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_NOT_FOLLOWED_BY_CHINESE");
            }
            sentences.set(index, value + "：" + sentences.get(index + 1));
            sentences.remove(index + 1);
        }
    }

    private boolean isClosingSuffixSequence(String value) {
        return !value.isBlank() && value.codePoints().allMatch(codePoint ->
                isQuoteMark(codePoint)
                        || Character.getType(codePoint)
                        == Character.END_PUNCTUATION);
    }

    private String technicalLabelRejection(String value) {
        if (value.length() > 120) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_LENGTH";
        }
        List<String> words = java.util.regex.Pattern.compile(
                        "[A-Za-z]+(?:[-'][A-Za-z]+)*")
                .matcher(value).results().map(java.util.regex.MatchResult::group)
                .toList();
        if (words.isEmpty()) {
            if (value.codePoints().anyMatch(Character::isDigit)) {
                return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_NUMBER_MARKER";
            }
            if (value.codePoints().anyMatch(codePoint ->
                    codePoint == 0x2026 || codePoint == 0x22EF)) {
                return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PLACEHOLDER_ELLIPSIS";
            }
            if (value.codePoints().allMatch(this::isPunctuationOrSymbol)) {
                return presentationMarkerReason(value);
            }
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_OTHER_SCRIPT";
        }
        if (words.size() > 6) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_WORD_COUNT";
        }
        if (words.size() > 1 && words.stream().anyMatch(word ->
                !Character.isUpperCase(word.charAt(0)))) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_CASE";
        }
        return null;
    }

    private String presentationMarkerReason(String value) {
        if (value.codePoints().allMatch(this::isQuoteMark)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_QUOTE_MARKER";
        }
        if (value.codePoints().anyMatch(this::isQuoteMark)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_MIXED_QUOTE_MARKER";
        }
        if (value.codePoints().allMatch(codePoint ->
                "*_`#~".indexOf(codePoint) >= 0)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_MARKDOWN_MARKER";
        }
        if (value.codePoints().anyMatch(codePoint ->
                "*_`#~".indexOf(codePoint) >= 0)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_MIXED_MARKDOWN_MARKER";
        }
        if (value.codePoints().anyMatch(codePoint ->
                "()[]{}<>（）【】《》「」".indexOf(codePoint) >= 0)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_MIXED_BRACKET_MARKER";
        }
        if (value.codePoints().anyMatch(codePoint ->
                Character.getType(codePoint) == Character.DASH_PUNCTUATION)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_MIXED_DASH_MARKER";
        }
        if (value.codePoints().allMatch(codePoint ->
                codePoint == '-' || codePoint == 0x2022 || codePoint == 0x2043
                        || codePoint == 0x25AA || codePoint == 0x25CF
                        || codePoint == 0x25CB || codePoint == 0x25A0)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_BULLET_MARKER";
        }
        if (value.codePoints().allMatch(codePoint ->
                codePoint == ':' || codePoint == 0xFF1A)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_COLON_MARKER";
        }
        if (value.codePoints().allMatch(codePoint ->
                "()[]{}<>（）【】《》「」".indexOf(codePoint) >= 0)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_BRACKET_MARKER";
        }
        if (value.codePoints().allMatch(codePoint ->
                Character.getType(codePoint) == Character.DASH_PUNCTUATION)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_DASH_MARKER";
        }
        if (value.codePoints().allMatch(codePoint ->
                Character.getType(codePoint) == Character.OTHER_PUNCTUATION)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_OTHER_PUNCTUATION";
        }
        if (value.codePoints().allMatch(codePoint ->
                Character.getType(codePoint) == Character.OTHER_SYMBOL)) {
            return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_OTHER_SYMBOL";
        }
        return "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_MIXED_SYMBOL_MARKER";
    }

    private boolean isQuoteMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || "'\"“”‘’「」『』".indexOf(codePoint) >= 0;
    }

    private boolean isPunctuationOrSymbol(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL;
    }

    private void appendSentence(
            StringBuilder target, String raw, int maximumCharacters) {
        String sentence = raw == null ? "" : raw.trim();
        if (sentence.endsWith("。")) {
            sentence = sentence.substring(0, sentence.length() - 1).trim();
        }
        if (sentence.isBlank() || sentence.length() > 1000
                || sentence.matches(".*[。.!?！？；;].*")
                || !sentence.matches(".*\\p{IsHan}.*")) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SENTENCE");
        }
        if (target.length() + sentence.length() + 1 > maximumCharacters) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                    "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_CHARACTER_LIMIT");
        }
        target.append(sentence).append('。');
    }

    private String closedText(JsonNode node, String field, Set<String> allowed) {
        String value = text(node, field, 64);
        if (!allowed.contains(value)) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        return value;
    }

    private JsonNode array(JsonNode node, String field) {
        requireField(node, field);
        JsonNode value = node.get(field);
        if (!value.isArray()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        return value;
    }

    private String text(JsonNode node, String field, int maximum) {
        requireField(node, field);
        JsonNode value = node.get(field);
        if (!value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maximum) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        return value.textValue().trim();
    }

    private void requireField(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
    }

    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
    }

    private void requireOnly(JsonNode node, Set<String> allowed) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
            }
        }
    }

    private void requireExact(JsonNode node, Set<String> expected) {
        requireOnly(node, expected);
        for (String field : expected) requireField(node, field);
        if (node.size() != expected.size()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        }
    }

    private StructuredOutputValidationException fail(
            StructuredOutputValidationException.Reason reason) {
        return new StructuredOutputValidationException(reason, reason.name());
    }

    private StructuredOutputValidationException fail(
            StructuredOutputValidationException.Reason reason,
            String diagnosticReason) {
        return new StructuredOutputValidationException(reason, diagnosticReason);
    }
}
