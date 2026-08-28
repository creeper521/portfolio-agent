package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Frozen EXPLANATION portion of the production v3 compiler/codec/validator at
 * commit b5cf941. This is test-only historical evidence, never a runtime path.
 */
final class LegacyGeneralV3Baseline {
    static final String BASELINE_COMMIT = "b5cf941";
    static final String COMPILER_BLOB =
            "0964c017e4a5a9836a10966154ed0249b58e8800";
    static final String CODEC_BLOB =
            "1b8033e36bab83eb2f04a43a210632617a28ed74";
    static final String VALIDATOR_BLOB =
            "43bb439bb7c9ec843e2d2c1ae644e9692e3645ef";
    static final String RULES_BLOB =
            "ca02f9dab5ae65a1d02ded6d6319ea5b49edb845";
    private static final String PROVIDER_SCHEMA_SHA256 =
            "606f8cf6c696c958ddb51d4144c42d858a99743e30181661f766c356c63e7b50";
    private static final String CANONICAL_SCHEMA_SHA256 =
            "458531f81da3c9039be7a70f40daf1010e4e86bfeba3cf613214bd5ca25b1e16";
    private static final Set<String> EXPLANATION_FIELDS = Set.of(
            "kind", "depth", "definitionSentences",
            "mechanismSentences", "caveats");
    private static final Set<String> CAVEAT_FIELDS =
            Set.of("kind", "sentences");
    private static final Set<String> ROOT_FIELDS =
            Set.of("topic", "statements", "caveats");
    private static final Set<String> STATEMENT_FIELDS = Set.of(
            "role", "text", "subject", "dimension", "aspects");
    private static final Set<String> CANONICAL_CAVEAT_FIELDS =
            Set.of("kind", "text");
    private static final Set<String> CAVEAT_KINDS = Set.of(
            "APPLICABILITY_BOUNDARY", "RISK", "EXCEPTION");
    private static final List<IntPredicate> QUOTE_MARK_CLASSIFIERS = List.of(
            codePoint -> Character.getType(codePoint)
                    == Character.INITIAL_QUOTE_PUNCTUATION,
            codePoint -> Character.getType(codePoint)
                    == Character.FINAL_QUOTE_PUNCTUATION,
            codePoint -> "'\"“”‘’「」『』".indexOf(codePoint) >= 0);
    private static final List<IntPredicate>
            PUNCTUATION_OR_SYMBOL_CLASSIFIERS = List.of(
                    Character::isWhitespace,
                    codePoint -> Character.getType(codePoint)
                            == Character.CONNECTOR_PUNCTUATION,
                    codePoint -> Character.getType(codePoint)
                            == Character.DASH_PUNCTUATION,
                    codePoint -> Character.getType(codePoint)
                            == Character.START_PUNCTUATION,
                    codePoint -> Character.getType(codePoint)
                            == Character.END_PUNCTUATION,
                    codePoint -> Character.getType(codePoint)
                            == Character.INITIAL_QUOTE_PUNCTUATION,
                    codePoint -> Character.getType(codePoint)
                            == Character.FINAL_QUOTE_PUNCTUATION,
                    codePoint -> Character.getType(codePoint)
                            == Character.OTHER_PUNCTUATION,
                    codePoint -> Character.getType(codePoint)
                            == Character.MATH_SYMBOL,
                    codePoint -> Character.getType(codePoint)
                            == Character.CURRENCY_SYMBOL,
                    codePoint -> Character.getType(codePoint)
                            == Character.MODIFIER_SYMBOL,
                    codePoint -> Character.getType(codePoint)
                            == Character.OTHER_SYMBOL);

    private LegacyGeneralV3Baseline() { }

    static void verifyRetainedSchemaIdentity() throws Exception {
        requireResourceHash(
                "/model-contracts/general.provider-draft.v3.schema.json",
                PROVIDER_SCHEMA_SHA256);
        requireResourceHash(
                "/model-contracts/general.draft.v2.schema.json",
                CANONICAL_SCHEMA_SHA256);
    }

    static JsonNode compile(
            ObjectMapper json,
            JsonNode draft,
            GeneralKnowledgeRequest request) {
        requireObject(draft, EXPLANATION_FIELDS);
        requireExact(draft, EXPLANATION_FIELDS);
        if (!"EXPLANATION".equals(text(draft, "kind", 64))
                || !request.getDepth().name().equals(
                text(draft, "depth", 16))) {
            throw new IllegalArgumentException("legacy branch mismatch");
        }
        Rule rule = rule(request.getDepth());
        ObjectNode canonical = json.createObjectNode();
        canonical.put("topic", request.getTopic());
        ArrayNode statements = canonical.putArray("statements");
        statements.add(statement(
                json,
                "DEFINITION",
                sentenceText(
                        draft,
                        "definitionSentences",
                        rule.providerSentencesPerRole(),
                        rule.providerSentencesPerRole(),
                        4000),
                rule.definitionAspects()));
        statements.add(statement(
                json,
                "MECHANISM",
                sentenceText(
                        draft,
                        "mechanismSentences",
                        rule.providerSentencesPerRole(),
                        rule.providerSentencesPerRole(),
                        4000),
                rule.mechanismAspects()));
        canonical.set("caveats", caveats(json, draft));
        return canonical;
    }

    static void decodeAndValidate(
            JsonNode root,
            GeneralKnowledgeRequest request) {
        requireObject(root, ROOT_FIELDS);
        String topic = requiredText(root, "topic");
        JsonNode statementValues = root.get("statements");
        if (statementValues == null || !statementValues.isArray()
                || statementValues.isEmpty() || statementValues.size() > 20) {
            throw new IllegalArgumentException("statements are invalid");
        }
        List<Statement> statements = new ArrayList<>();
        for (JsonNode value : statementValues) {
            requireObject(value, STATEMENT_FIELDS);
            statements.add(new Statement(
                    Role.valueOf(requiredText(value, "role")),
                    requiredText(value, "text"),
                    optionalText(value, "subject"),
                    optionalText(value, "dimension"),
                    aspectSet(value)));
        }
        JsonNode caveatValues = root.get("caveats");
        if (caveatValues == null || !caveatValues.isArray()
                || caveatValues.size() > 10) {
            throw new IllegalArgumentException("caveats are invalid");
        }
        List<Caveat> caveats = new ArrayList<>();
        for (JsonNode value : caveatValues) {
            requireObject(value, CANONICAL_CAVEAT_FIELDS);
            String caveatText = requiredText(value, "text");
            if (caveatText.length() > 1000) {
                throw new IllegalArgumentException("caveat text is invalid");
            }
            caveats.add(new Caveat(
                    CaveatKind.valueOf(requiredText(value, "kind")),
                    caveatText));
        }
        validate(request, new Draft(topic, statements, caveats));
    }

    private static void validate(
            GeneralKnowledgeRequest request,
            Draft draft) {
        if (!request.getTopic().equals(draft.topic())
                || draft.statements().size() != 2
                || draft.statements().get(0).role() != Role.DEFINITION
                || draft.statements().get(1).role() != Role.MECHANISM) {
            throw new IllegalArgumentException("legacy explanation invalid");
        }
        Rule rule = rule(request.getDepth());
        int sentenceCount = draft.statements().stream()
                .mapToInt(value -> countChineseSentences(value.text()))
                .sum();
        if (sentenceCount < rule.minimumCanonicalSentences()
                || sentenceCount > rule.maximumCanonicalSentences()
                || !draft.statements().get(0).aspects().contains(
                rule.definitionAspects().get(0))
                || !draft.statements().get(1).aspects().contains(
                rule.mechanismAspects().get(0))) {
            throw new IllegalArgumentException("legacy explanation quality invalid");
        }
        Set<Aspect> actual = EnumSet.noneOf(Aspect.class);
        draft.statements().forEach(value -> actual.addAll(value.aspects()));
        if (!actual.equals(rule.coverage())) {
            throw new IllegalArgumentException("legacy coverage invalid");
        }
        Set<String> texts = new HashSet<>();
        Set<CaveatKind> kinds = EnumSet.noneOf(CaveatKind.class);
        for (Caveat caveat : draft.caveats()) {
            int count = countChineseSentences(caveat.text());
            if (count < 1 || count > 2
                    || !texts.add(normalize(caveat.text()))
                    || !kinds.add(caveat.kind())) {
                throw new IllegalArgumentException("legacy caveat invalid");
            }
        }
    }

    private static Rule rule(UserGoalProposal.Depth depth) {
        return switch (depth) {
            case CONCISE -> new Rule(
                    1,
                    2,
                    2,
                    List.of(Aspect.DEFINITION),
                    List.of(Aspect.MECHANISM));
            case STANDARD -> new Rule(
                    2,
                    4,
                    6,
                    List.of(Aspect.DEFINITION, Aspect.TYPICAL_USAGE),
                    List.of(Aspect.MECHANISM, Aspect.APPLICABILITY_BOUNDARY));
            case DETAILED -> new Rule(
                    4,
                    8,
                    12,
                    List.of(
                            Aspect.DEFINITION,
                            Aspect.TYPICAL_USAGE,
                            Aspect.COMMON_MISCONCEPTION),
                    List.of(
                            Aspect.MECHANISM,
                            Aspect.APPLICABILITY_BOUNDARY,
                            Aspect.TRADE_OFF,
                            Aspect.BOUNDARY_CONDITION));
        };
    }

    private static ObjectNode statement(
            ObjectMapper json,
            String role,
            String value,
            List<Aspect> aspects) {
        ObjectNode statement = json.createObjectNode();
        statement.put("role", role);
        statement.put("text", value);
        ArrayNode values = statement.putArray("aspects");
        aspects.forEach(aspect -> values.add(aspect.name()));
        return statement;
    }

    private static ArrayNode caveats(ObjectMapper json, JsonNode draft) {
        JsonNode values = array(draft, "caveats");
        if (values.size() > 10) {
            throw new IllegalArgumentException("legacy caveats invalid");
        }
        ArrayNode result = json.createArrayNode();
        for (JsonNode value : values) {
            requireObject(value, CAVEAT_FIELDS);
            requireExact(value, CAVEAT_FIELDS);
            String kind = text(value, "kind", 64);
            if (!CAVEAT_KINDS.contains(kind)) {
                throw new IllegalArgumentException("legacy caveat kind invalid");
            }
            ObjectNode caveat = result.addObject();
            caveat.put("kind", kind);
            caveat.put("text", sentenceText(
                    value, "sentences", 1, 2, 2000));
        }
        return result;
    }

    private static String sentenceText(
            JsonNode node,
            String field,
            int minimum,
            int maximum,
            int maximumCharacters) {
        List<String> values = sentenceValues(node, field);
        if (values.size() < minimum || values.size() > maximum) {
            throw new IllegalArgumentException("legacy sentence count invalid");
        }
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            appendSentence(result, value, maximumCharacters);
        }
        return result.toString();
    }

    private static List<String> sentenceValues(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("legacy sentence missing");
        }
        List<String> sentences = new ArrayList<>();
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException(
                            "legacy sentence item type invalid");
                }
                sentences.addAll(parseSentenceSequence(item.textValue()));
            }
        } else if (value.isTextual()) {
            sentences.addAll(parseSentenceSequence(value.textValue()));
        } else {
            throw new IllegalArgumentException("legacy sentence branch invalid");
        }
        bindTechnicalLabels(sentences);
        return List.copyOf(sentences);
    }

    private static List<String> parseSentenceSequence(String raw) {
        String sequence = raw == null ? "" : raw.trim();
        if (sequence.isBlank() || sequence.matches(".*[.!?！？；;].*")) {
            throw new IllegalArgumentException("legacy sentence format invalid");
        }
        String[] parts = sequence.split("。", -1);
        int length = sequence.endsWith("。")
                ? parts.length - 1 : parts.length;
        if (length < 1) {
            throw new IllegalArgumentException("legacy sentence format invalid");
        }
        List<String> sentences = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            String sentence = parts[index].trim();
            if (sentence.isBlank()) {
                throw new IllegalArgumentException("legacy empty sentence");
            }
            sentences.add(sentence);
        }
        return List.copyOf(sentences);
    }

    private static void bindTechnicalLabels(List<String> sentences) {
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
            if (rejection != null || index + 1 >= sentences.size()
                    || !sentences.get(index + 1).matches(".*\\p{IsHan}.*")) {
                throw new IllegalArgumentException("legacy technical label invalid");
            }
            sentences.set(index, value + "：" + sentences.get(index + 1));
            sentences.remove(index + 1);
        }
    }

    private static String technicalLabelRejection(String value) {
        if (value.length() > 120) {
            return "TECHNICAL_LABEL_LENGTH";
        }
        List<String> words = Pattern.compile("[A-Za-z]+(?:[-'][A-Za-z]+)*")
                .matcher(value)
                .results()
                .map(MatchResult::group)
                .toList();
        if (words.isEmpty()) {
            if (value.codePoints().anyMatch(Character::isDigit)
                    || value.codePoints().anyMatch(codePoint ->
                    codePoint == 0x2026 || codePoint == 0x22EF)
                    || value.codePoints().allMatch(
                    LegacyGeneralV3Baseline::isPunctuationOrSymbol)) {
                return "TECHNICAL_LABEL_PRESENTATION";
            }
            return "TECHNICAL_LABEL_OTHER_SCRIPT";
        }
        if (words.size() > 6
                || words.size() > 1 && words.stream().anyMatch(word ->
                !Character.isUpperCase(word.charAt(0)))) {
            return "TECHNICAL_LABEL_WORDS";
        }
        return null;
    }

    private static void appendSentence(
            StringBuilder target,
            String raw,
            int maximumCharacters) {
        String sentence = raw == null ? "" : raw.trim();
        if (sentence.endsWith("。")) {
            sentence = sentence.substring(0, sentence.length() - 1).trim();
        }
        if (sentence.isBlank() || sentence.length() > 1000
                || sentence.matches(".*[。.!?！？；;].*")
                || !sentence.matches(".*\\p{IsHan}.*")
                || target.length() + sentence.length() + 1
                > maximumCharacters) {
            throw new IllegalArgumentException("legacy sentence invalid");
        }
        target.append(sentence).append('。');
    }

    private static int countChineseSentences(String text) {
        if (!text.endsWith("。") || text.matches(".*[.!?！？].*")) {
            throw new IllegalArgumentException("legacy boundary invalid");
        }
        List<String> sentences = Pattern.compile("。")
                .splitAsStream(text)
                .filter(value -> !value.isBlank())
                .toList();
        if (sentences.isEmpty() || sentences.stream().anyMatch(value ->
                !value.matches(".*[\\p{IsHan}].*"))) {
            throw new IllegalArgumentException("legacy language invalid");
        }
        return sentences.size();
    }

    private static Set<Aspect> aspectSet(JsonNode node) {
        JsonNode values = node.get("aspects");
        if (values == null || !values.isArray() || values.size() > 10) {
            throw new IllegalArgumentException("legacy aspects invalid");
        }
        LinkedHashSet<Aspect> decoded = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual()
                    || !decoded.add(Aspect.valueOf(value.textValue()))) {
                throw new IllegalArgumentException("legacy aspects invalid");
            }
        }
        return Set.copyOf(decoded);
    }

    private static JsonNode array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("legacy array invalid");
        }
        return value;
    }

    private static String text(JsonNode node, String field, int maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > maximum) {
            throw new IllegalArgumentException("legacy text invalid");
        }
        return value.textValue().trim();
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("legacy required text missing");
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > 4000) {
            throw new IllegalArgumentException("legacy optional text invalid");
        }
        return value.textValue().trim();
    }

    private static void requireObject(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("legacy object invalid");
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                throw new IllegalArgumentException("legacy unknown field");
            }
        }
    }

    private static void requireExact(JsonNode node, Set<String> expected) {
        requireObject(node, expected);
        for (String field : expected) {
            if (!node.has(field) || node.get(field).isNull()) {
                throw new IllegalArgumentException("legacy field missing");
            }
        }
        if (node.size() != expected.size()) {
            throw new IllegalArgumentException("legacy field conflict");
        }
    }

    private static boolean isClosingSuffixSequence(String value) {
        return !value.isBlank() && value.codePoints().allMatch(codePoint ->
                isQuoteMark(codePoint)
                        || Character.getType(codePoint)
                        == Character.END_PUNCTUATION);
    }

    private static boolean isQuoteMark(int codePoint) {
        return classifiedBy(codePoint, QUOTE_MARK_CLASSIFIERS);
    }

    private static boolean isPunctuationOrSymbol(int codePoint) {
        return classifiedBy(codePoint, PUNCTUATION_OR_SYMBOL_CLASSIFIERS);
    }

    private static boolean classifiedBy(
            int codePoint,
            List<IntPredicate> classifiers) {
        return classifiers.stream().anyMatch(value -> value.test(codePoint));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static void requireResourceHash(
            String resource,
            String expected) throws Exception {
        try (InputStream stream = LegacyGeneralV3Baseline.class
                .getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("legacy schema is missing");
            }
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(stream.readAllBytes());
            StringBuilder actual = new StringBuilder();
            for (byte value : digest) {
                actual.append(String.format("%02x", value & 0xff));
            }
            if (!expected.equals(actual.toString())) {
                throw new IllegalStateException("legacy schema identity changed");
            }
        }
    }

    private record Rule(
            int providerSentencesPerRole,
            int minimumCanonicalSentences,
            int maximumCanonicalSentences,
            List<Aspect> definitionAspects,
            List<Aspect> mechanismAspects) {
        private Set<Aspect> coverage() {
            Set<Aspect> values = EnumSet.noneOf(Aspect.class);
            values.addAll(definitionAspects);
            values.addAll(mechanismAspects);
            return Set.copyOf(values);
        }
    }

    private record Draft(
            String topic,
            List<Statement> statements,
            List<Caveat> caveats) {
        private Draft {
            statements = List.copyOf(statements);
            caveats = List.copyOf(caveats);
        }
    }

    private record Statement(
            Role role,
            String text,
            String subject,
            String dimension,
            Set<Aspect> aspects) { }

    private record Caveat(CaveatKind kind, String text) { }

    private enum Role { DEFINITION, MECHANISM, COMPARISON }

    private enum Aspect {
        DEFINITION,
        MECHANISM,
        TYPICAL_USAGE,
        APPLICABILITY_BOUNDARY,
        TRADE_OFF,
        COMMON_MISCONCEPTION,
        BOUNDARY_CONDITION
    }

    private enum CaveatKind { APPLICABILITY_BOUNDARY, RISK, EXCEPTION }
}
