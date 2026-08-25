package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Strict decoder for untrusted provider output. Unknown or shape-mismatched fields fail closed.
 *
 * <p>通用能力草稿的严格解码器：把不可信的 Provider 原始输出解析为结构化 {@link Draft}。
 * fail-closed 语义——出现未知字段、形状不符、长度超限（文本 ≤4000、caveat 文本 ≤1000）、
 * 数量超限（statements 1..20、caveats ≤10、aspects ≤10 且不重复）、尾随 token 等任何
 * 偏差都抛 {@link IllegalArgumentException}，绝不静默容忍或猜测修复。
 */
public final class GeneralDraftCodec {
    public static final String SCHEMA_VERSION = "general.draft.v2";
    private static final Set<String> ROOT_FIELDS = Set.of("topic", "statements", "caveats");
    private static final Set<String> STATEMENT_FIELDS = Set.of(
            "role", "text", "subject", "dimension", "aspects");
    private static final Set<String> CAVEAT_FIELDS = Set.of("kind", "text");
    private final ObjectMapper objectMapper;

    public GeneralDraftCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * 解码 Provider 原始输出为 {@link Draft}：逐层校验字段白名单、必填/可选文本、
     * 枚举与数量约束，任一违规立即失败。
     *
     * @param raw Provider 返回的原始 JSON 文本
     * @throws IllegalArgumentException 任何结构、白名单、长度或数量校验不通过
     */
    public Draft decode(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            requireObject(root, ROOT_FIELDS, "root");
            String topic = requiredText(root, "topic");
            JsonNode statementsNode = root.get("statements");
            if (statementsNode == null || !statementsNode.isArray()
                    || statementsNode.isEmpty() || statementsNode.size() > 20) {
                throw new IllegalArgumentException("statements are invalid");
            }
            List<StatementDraft> statements = new ArrayList<>();
            for (JsonNode node : statementsNode) {
                requireObject(node, STATEMENT_FIELDS, "statement");
                GeneralSemanticResult.Role role = GeneralSemanticResult.Role.valueOf(requiredText(node, "role"));
                statements.add(new StatementDraft(
                        role, requiredText(node, "text"), optionalText(node, "subject"),
                        optionalText(node, "dimension"), enumSet(node, "aspects", Aspect.class)));
            }
            JsonNode caveatsNode = root.get("caveats");
            if (caveatsNode == null || !caveatsNode.isArray() || caveatsNode.size() > 10) {
                throw new IllegalArgumentException("caveats are invalid");
            }
            List<CaveatDraft> caveats = new ArrayList<>();
            for (JsonNode node : caveatsNode) {
                requireObject(node, CAVEAT_FIELDS, "caveat");
                String text = requiredText(node, "text");
                if (text.length() > 1000) {
                    throw new IllegalArgumentException("caveat text is invalid");
                }
                caveats.add(new CaveatDraft(
                        CaveatKind.valueOf(requiredText(node, "kind")),
                        text));
            }
            return new Draft(topic, statements, caveats);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("general draft is invalid", exception);
        }
    }

    /** 要求 JSON 节点为对象且字段名全部在白名单内；出现未知字段即拒绝（fail-closed）。 */
    private void requireObject(JsonNode node, Set<String> allowed, String name) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(name + " must be an object");
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) throw new IllegalArgumentException(name + " contains unknown fields");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    /** 可选文本：缺失或 null 返回 null；存在则必须非空白且 ≤4000 字符，否则拒绝。 */
    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 4000) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.textValue().trim();
    }

    /** 枚举集合：必须为数组、≤10 项、值均为合法枚举且不重复（重复视为无效输入）。 */
    private <E extends Enum<E>> Set<E> enumSet(
            JsonNode node, String field, Class<E> type) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray() || values.size() > 10) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        java.util.LinkedHashSet<E> decoded = new java.util.LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || !decoded.add(Enum.valueOf(type, value.textValue()))) {
                throw new IllegalArgumentException(field + " is invalid");
            }
        }
        return Set.copyOf(decoded);
    }

    /** 解码成功的完整草稿：主题 + 陈述列表 + 限定说明列表，列表不可变。 */
    public record Draft(
            String topic, List<StatementDraft> statements, List<CaveatDraft> caveats) {
        public Draft {
            statements = List.copyOf(statements);
            caveats = List.copyOf(caveats);
        }
    }
    /** 单条知识陈述草稿：角色、正文、可选主题/维度标注与覆盖侧面集合。 */
    public record StatementDraft(
            GeneralSemanticResult.Role role, String text, String subject, String dimension,
            Set<Aspect> aspects) { }
    /** 单条限定说明草稿：限定类别与正文（长度已在解码时约束 ≤1000）。 */
    public record CaveatDraft(CaveatKind kind, String text) { }
    /** 陈述覆盖的知识侧面：定义、机制、典型用法、适用边界、权衡、常见误解、边界条件。 */
    public enum Aspect {
        DEFINITION, MECHANISM, TYPICAL_USAGE, APPLICABILITY_BOUNDARY,
        TRADE_OFF, COMMON_MISCONCEPTION, BOUNDARY_CONDITION
    }
    /** 限定说明类别：适用边界、风险、例外情形。 */
    public enum CaveatKind { APPLICABILITY_BOUNDARY, RISK, EXCEPTION }
}
