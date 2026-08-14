package com.portfolio.agent.answer.general.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterialDraft;
import com.portfolio.agent.answer.general.domain.GeneralStatementRole;
import com.portfolio.agent.answer.general.domain.GeneralSupportKind;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class GeneralAnswerMaterialDraftCodec {
    public static final String SCHEMA_VERSION = "general-material-v1";
    private static final int MAX_STATEMENTS = 16;
    private static final int MAX_CODE_POINTS = 8_000;
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public GeneralAnswerMaterialDraft decode(String raw) {
        try {
            if (raw == null || raw.isBlank()) fail();
            JsonNode root = mapper.readTree(raw);
            allowed(root, "schemaVersion", "topic", "statements", "caveats", "metadata");
            text(root, "schemaVersion");
            if (!SCHEMA_VERSION.equals(root.get("schemaVersion").textValue())) fail();
            text(root, "topic");
            JsonNode statements = array(root, "statements", 1, MAX_STATEMENTS);
            List<GeneralAnswerMaterialDraft.StatementDraft> values = new ArrayList<>();
            int characters = root.get("topic").textValue().codePointCount(0, root.get("topic").textValue().length());
            for (JsonNode statement : statements) {
                allowed(statement, "statementAlias", "text", "role", "conceptTags", "supportKind", "publicSourceKeys");
                text(statement, "statementAlias"); text(statement, "text"); text(statement, "role"); text(statement, "supportKind");
                characters += statement.get("text").textValue().codePointCount(0, statement.get("text").textValue().length());
                if (characters > MAX_CODE_POINTS) fail();
                values.add(new GeneralAnswerMaterialDraft.StatementDraft(
                        statement.get("statementAlias").textValue(), statement.get("text").textValue(),
                        enumValue(GeneralStatementRole.class, statement.get("role").textValue()),
                        textSet(statement, "conceptTags"),
                        enumValue(GeneralSupportKind.class, statement.get("supportKind").textValue()),
                        textList(statement, "publicSourceKeys", 0, 0)));
            }
            List<GeneralAnswerMaterialDraft.CaveatDraft> caveats = new ArrayList<>();
            JsonNode caveatNode = array(root, "caveats", 0, 8);
            for (JsonNode caveat : caveatNode) {
                allowed(caveat, "alias", "text"); text(caveat, "alias"); text(caveat, "text");
                caveats.add(new GeneralAnswerMaterialDraft.CaveatDraft(caveat.get("alias").textValue(), caveat.get("text").textValue()));
            }
            JsonNode metadata = root.get("metadata");
            allowed(metadata, "contentVersion", "audienceRole", "discourseAliases");
            text(metadata, "contentVersion");
            return new GeneralAnswerMaterialDraft(root.get("topic").textValue(), values, caveats,
                    new GeneralAnswerMaterialDraft.MetadataDraft(metadata.get("contentVersion").textValue(),
                            optionalText(metadata, "audienceRole"), textList(metadata, "discourseAliases", 0, 8)));
        } catch (GeneralMaterialDecodingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeneralMaterialDecodingException(exception);
        }
    }

    private static JsonNode array(JsonNode parent, String name, int min, int max) {
        JsonNode value = parent.get(name);
        if (value == null || !value.isArray() || value.size() < min || value.size() > max) fail();
        return value;
    }
    private static List<String> textList(JsonNode parent, String name, int min, int max) {
        JsonNode value = array(parent, name, min, max); List<String> result = new ArrayList<>();
        for (JsonNode item : value) { if (!item.isTextual() || item.textValue().isBlank()) fail(); result.add(item.textValue()); }
        return List.copyOf(result);
    }
    private static Set<String> textSet(JsonNode parent, String name) { return Set.copyOf(textList(parent, name, 0, 8)); }
    private static String optionalText(JsonNode parent, String name) {
        JsonNode value = parent.get(name); if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().isBlank()) fail(); return value.textValue();
    }
    private static void text(JsonNode parent, String name) { if (!parent.has(name) || !parent.get(name).isTextual() || parent.get(name).textValue().isBlank()) fail(); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) { try { return Enum.valueOf(type, value); } catch (Exception e) { fail(); return null; } }
    private static void allowed(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) fail(); Set<String> allowed = Set.of(fields); Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) if (!allowed.contains(it.next().getKey())) fail();
        for (String field : fields) if (!node.has(field)) fail();
    }
    private static void fail() { throw new GeneralMaterialDecodingException(); }
}
