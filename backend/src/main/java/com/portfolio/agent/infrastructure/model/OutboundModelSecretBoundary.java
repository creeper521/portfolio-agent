package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provider 出站正文的最小 secret-like 失败关闭边界。
 *
 * <p>本边界不是通用 DLP：它只遍历已经组装好的 JSON payload 的文本叶子，
 * 忽略 JSON 字段名，并只拒绝冻结的高置信形态——API key/鉴权标签或
 * secret/password/token 类标签后的有界赋值、仓库隐私规则已有的两类裸密钥
 * 指纹，或 PEM 私钥头。仅出现 API key 等技术术语、空赋值、闭集教育状态和
 * 闭集占位符不会被拒绝。包装占位符必须完整闭合并停在闭集赋值边界；仅额外
 * 接受一层引号中的完整 {@code ${SECRET_LABEL}} 环境变量占位符，不递归解析。
 * 命中时只抛闭集失败码，不保留或回显命中文本。</p>
 */
final class OutboundModelSecretBoundary {
    private static final int MAX_ASSIGNMENT_KEY_CHARS = 96;
    private static final int MAX_ASSIGNMENT_VALUE_CHARS = 512;
    private static final int MIN_STRUCTURED_SECRET_CHARS = 16;
    private static final int MIN_LETTERS_ONLY_SECRET_CHARS = 24;
    private static final Pattern PRIVATE_KEY_MARKER = Pattern.compile(
            "-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STANDALONE_SECRET = Pattern.compile(
            "(?:\\bsk-[a-z0-9_-]{20,}\\b|"
                    + "\\b[0-9a-f]{32}\\.[a-z0-9_-]{16,}\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> EDUCATIONAL_VALUES = Set.of(
            "required",
            "optional",
            "configured",
            "authentication",
            "required-for-authentication",
            "optional-when-configured",
            "authentication-required",
            "not-configured-yet");
    private static final Set<String> SIMPLE_PLACEHOLDER_VALUES = Set.of(
            "redacted",
            "placeholder",
            "changeme",
            "example",
            "example-value",
            "dummy-value");
    private static final Set<String> PLACEHOLDER_SUBJECTS = Set.of(
            "api-key", "token", "secret", "password", "credential", "cookie");

    private OutboundModelSecretBoundary() { }

    static void assertSafe(Map<String, ?> outboundPayload) {
        Map<String, ?> root = Objects.requireNonNull(
                outboundPayload, "outboundPayload");
        Deque<Object> pending = new ArrayDeque<>();
        root.values().forEach(value -> addIfPresent(pending, value));
        while (!pending.isEmpty()) {
            Object current = pending.removeFirst();
            if (current instanceof CharSequence text) {
                assertTextSafe(text.toString());
            } else if (current instanceof Map<?, ?> map) {
                map.values().forEach(value -> addIfPresent(pending, value));
            } else if (current instanceof Iterable<?> iterable) {
                iterable.forEach(value -> addIfPresent(pending, value));
            } else if (current instanceof JsonNode node) {
                inspectJsonNode(pending, node);
            }
        }
    }

    private static void inspectJsonNode(Deque<Object> pending, JsonNode node) {
        if (node.isTextual()) {
            assertTextSafe(node.textValue());
        } else if (node.isContainerNode()) {
            node.elements().forEachRemaining(value -> addIfPresent(pending, value));
        }
    }

    private static void addIfPresent(Deque<Object> pending, Object value) {
        if (value != null) {
            pending.addLast(value);
        }
    }

    private static void assertTextSafe(String text) {
        if (PRIVATE_KEY_MARKER.matcher(text).find()
                || STANDALONE_SECRET.matcher(text).find()) {
            reject();
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if ((character == ':' || character == '=')
                    && isHighConfidenceSecretAssignment(text, index)) {
                reject();
            }
        }
    }

    private static boolean isHighConfidenceSecretAssignment(
            String text, int separatorIndex) {
        String key = assignmentKeyBefore(text, separatorIndex);
        if (!isSecretLabel(key)) {
            return false;
        }
        ParsedValue parsed = assignmentValueAfter(
                text, separatorIndex, isAuthorizationLabel(key));
        String candidate = parsed.value();
        if (parsed.wrapper() != Wrapper.NONE
                && (!parsed.closed()
                || !hasLegalAssignmentBoundary(text, parsed.end()))) {
            return true;
        }
        if (candidate.isEmpty()) {
            return false;
        }
        if (isEducationalOrPlaceholder(parsed)) {
            return false;
        }
        if (parsed.wrapper() != Wrapper.NONE) {
            return true;
        }
        if (parsed.authorizationScheme()) {
            return candidate.length() >= MIN_STRUCTURED_SECRET_CHARS;
        }
        if (candidate.length() < MIN_STRUCTURED_SECRET_CHARS) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigitOrTokenSymbol = false;
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (Character.isLetter(character)) {
                hasLetter = true;
            } else if (Character.isDigit(character)
                    || "_./+@:=~-".indexOf(character) >= 0) {
                hasDigitOrTokenSymbol = true;
            }
        }
        return hasLetter && (hasDigitOrTokenSymbol
                || candidate.length() >= MIN_LETTERS_ONLY_SECRET_CHARS);
    }

    private static String assignmentKeyBefore(String text, int separatorIndex) {
        int end = separatorIndex - 1;
        while (end >= 0 && (Character.isWhitespace(text.charAt(end))
                || text.charAt(end) == '\'' || text.charAt(end) == '"')) {
            end--;
        }
        int start = end;
        int inspected = 0;
        while (start >= 0 && inspected < MAX_ASSIGNMENT_KEY_CHARS
                && isAssignmentKeyCharacter(text.charAt(start))) {
            start--;
            inspected++;
        }
        return normalizeWords(text.substring(start + 1, end + 1), '_');
    }

    private static ParsedValue assignmentValueAfter(
            String text, int separatorIndex, boolean authorizationLabel) {
        int start = separatorIndex + 1;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        boolean scheme = false;
        if (authorizationLabel) {
            int schemeEnd = start;
            while (schemeEnd < text.length()
                    && isAsciiLetter(text.charAt(schemeEnd))) {
                schemeEnd++;
            }
            String value = text.substring(start, schemeEnd);
            if ((value.equalsIgnoreCase("bearer")
                    || value.equalsIgnoreCase("basic"))
                    && schemeEnd < text.length()
                    && Character.isWhitespace(text.charAt(schemeEnd))) {
                scheme = true;
                start = schemeEnd + 1;
                while (start < text.length()
                        && Character.isWhitespace(text.charAt(start))) {
                    start++;
                }
            }
        }
        Wrapper wrapper = Wrapper.NONE;
        char closing = 0;
        if (start + 1 < text.length()
                && text.charAt(start) == '$' && text.charAt(start + 1) == '{') {
            wrapper = Wrapper.ENVIRONMENT;
            closing = '}';
            start += 2;
        } else if (start < text.length()) {
            wrapper = Wrapper.fromOpening(text.charAt(start));
            if (wrapper != Wrapper.NONE) {
                closing = wrapper.closing();
                start++;
            }
        }
        int end = start;
        if (wrapper == Wrapper.NONE) {
            while (end < text.length()
                    && end - start < MAX_ASSIGNMENT_VALUE_CHARS
                    && isAssignmentValueCharacter(text.charAt(end))) {
                end++;
            }
        } else {
            while (end < text.length()
                    && end - start < MAX_ASSIGNMENT_VALUE_CHARS
                    && text.charAt(end) != closing) {
                end++;
            }
        }
        boolean closed = wrapper == Wrapper.NONE
                || end < text.length() && text.charAt(end) == closing;
        int parsedEnd = closed && wrapper != Wrapper.NONE ? end + 1 : end;
        return new ParsedValue(
                text.substring(start, end).trim(), scheme, wrapper,
                closed, parsedEnd);
    }

    private static boolean isSecretLabel(String key) {
        return key.equals("api_key") || key.endsWith("_api_key")
                || isLabelOrSuffix(key, "authorization")
                || isLabelOrSuffix(key, "cookie")
                || isLabelOrSuffix(key, "credential")
                || isLabelOrSuffix(key, "credentials")
                || isLabelOrSuffix(key, "secret")
                || isLabelOrSuffix(key, "password")
                || isLabelOrSuffix(key, "token");
    }

    private static boolean isAuthorizationLabel(String key) {
        return isLabelOrSuffix(key, "authorization");
    }

    private static boolean isLabelOrSuffix(String key, String label) {
        return key.equals(label) || key.endsWith("_" + label);
    }

    private static boolean isEducationalOrPlaceholder(ParsedValue parsed) {
        String normalized = normalizePlaceholderWords(parsed.value());
        if (normalized == null) {
            return isQuotedEnvironmentPlaceholder(parsed);
        }
        if (EDUCATIONAL_VALUES.contains(normalized)
                || SIMPLE_PLACEHOLDER_VALUES.contains(normalized)) {
            return true;
        }
        for (String subject : PLACEHOLDER_SUBJECTS) {
            if (normalized.equals("your-" + subject)
                    || normalized.equals("your-" + subject + "-here")
                    || normalized.equals(subject + "-here")) {
                return true;
            }
        }
        if (isDirectPlaceholderWrapper(parsed.wrapper())
                && PLACEHOLDER_SUBJECTS.contains(normalized)) {
            return true;
        }
        if (parsed.wrapper() == Wrapper.ENVIRONMENT
                && isEnvironmentPlaceholder(parsed.value())) {
            return true;
        }
        return isQuotedEnvironmentPlaceholder(parsed);
    }

    private static boolean isDirectPlaceholderWrapper(Wrapper wrapper) {
        return wrapper == Wrapper.BRACKET || wrapper == Wrapper.ANGLE
                || wrapper == Wrapper.PARENTHESIS
                || wrapper == Wrapper.BRACE;
    }

    private static boolean isQuotedEnvironmentPlaceholder(ParsedValue parsed) {
        if (!(parsed.wrapper() == Wrapper.SINGLE_QUOTE
                || parsed.wrapper() == Wrapper.DOUBLE_QUOTE)) {
            return false;
        }
        String value = parsed.value();
        if (value.length() < 4 || !value.startsWith("${")
                || !value.endsWith("}")) {
            return false;
        }
        return isEnvironmentPlaceholder(
                value.substring(2, value.length() - 1));
    }

    private static boolean isEnvironmentPlaceholder(String value) {
        if (value.isEmpty() || value.length() > MAX_ASSIGNMENT_KEY_CHARS) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(isAsciiLetter(character) || Character.isDigit(character)
                    || character == '_')) {
                return false;
            }
        }
        return isSecretLabel(normalizeWords(value, '_'));
    }

    private static String normalizeWords(String value, char separator) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        boolean pendingSeparator = false;
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (isAsciiLetter(character) || Character.isDigit(character)) {
                if (pendingSeparator && !normalized.isEmpty()) {
                    normalized.append(separator);
                }
                normalized.append(character);
                pendingSeparator = false;
            } else {
                pendingSeparator = true;
            }
        }
        return normalized.toString();
    }

    private static String normalizePlaceholderWords(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(isAsciiLetter(character) || Character.isDigit(character)
                    || character == '_' || character == '-'
                    || Character.isWhitespace(character))) {
                return null;
            }
        }
        return normalizeWords(value, '-');
    }

    private static boolean hasLegalAssignmentBoundary(
            String text, int parsedEnd) {
        if (parsedEnd == text.length()) {
            return true;
        }
        char boundary = text.charAt(parsedEnd);
        return Character.isWhitespace(boundary)
                || ",;}]）、，；。！？!?".indexOf(boundary) >= 0;
    }

    private static boolean isAssignmentKeyCharacter(char character) {
        return isAsciiLetter(character) || Character.isDigit(character)
                || character == '_' || character == '-'
                || Character.isWhitespace(character);
    }

    private static boolean isAssignmentValueCharacter(char character) {
        return isAsciiLetter(character) || Character.isDigit(character)
                || "_./+@:=~-".indexOf(character) >= 0;
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z');
    }

    private static void reject() {
        throw new StructuredModelFailure(
                StructuredModelFailure.Code.OUTBOUND_SECRET_LIKE_REJECTED,
                StructuredModelFailure.Reason.SECRET_LIKE_CONTENT);
    }

    private record ParsedValue(
            String value, boolean authorizationScheme, Wrapper wrapper,
            boolean closed, int end) { }

    private enum Wrapper {
        NONE((char) 0),
        SINGLE_QUOTE('\''),
        DOUBLE_QUOTE('"'),
        BRACKET(']'),
        ANGLE('>'),
        PARENTHESIS(')'),
        BRACE('}'),
        ENVIRONMENT('}');

        private final char closing;

        Wrapper(char closing) {
            this.closing = closing;
        }

        char closing() {
            return closing;
        }

        static Wrapper fromOpening(char opening) {
            return switch (opening) {
                case '\'' -> SINGLE_QUOTE;
                case '"' -> DOUBLE_QUOTE;
                case '[' -> BRACKET;
                case '<' -> ANGLE;
                case '(' -> PARENTHESIS;
                case '{' -> BRACE;
                default -> NONE;
            };
        }
    }
}
