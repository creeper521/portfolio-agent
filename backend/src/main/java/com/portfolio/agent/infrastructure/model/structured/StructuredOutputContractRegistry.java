package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Canonical wire contract 深模块：闭集解析、严格 parse-once、本地 JSON Schema 校验与
 * 安全失败分类均由同一入口完成。
 */
public final class StructuredOutputContractRegistry {
    private static final int MAX_PAYLOAD_CHARACTERS = 20000;
    private static final int MAX_NESTING_DEPTH = 16;
    private static final int MAX_TOTAL_ARRAY_ELEMENTS = 64;
    private static final List<String> SAFE_SCHEMA_FIELDS = List.of(
            "kind", "route", "candidateKey", "goal", "clarification",
            "recentReference", "message", "goalKey", "goalKind", "inputAnchor",
            "subjectCandidates", "requestedOutputs", "knowledgeRequirement",
            "parameters", "text", "start", "reference", "basis", "anchor",
            "facets", "depth", "dimensions", "requestedSize", "constraints",
            "topicAnchor", "subjectAnchors", "conceptAnchor", "portfolioFacet",
            "field", "blockedGoal", "subjects", "portfolioDepth",
            "unresolvedField", "askedFields", "remainingFields", "goalId",
            "sectionId", "decision", "inputText", "subjectTexts", "conceptText",
            "topicText", "topic", "statements", "caveats", "role", "subject",
            "dimension", "aspects", "definitionSentences",
            "mechanismSentences", "comparisonSentences", "sentences", "prompt",
            "subjectIndex");
    private final ObjectMapper strictMapper;
    private final Map<StructuredContractRef, StructuredOutputContract> contracts;

    private StructuredOutputContractRegistry(ObjectMapper strictMapper,
            Map<StructuredContractRef, StructuredOutputContract> contracts) {
        this.strictMapper = strictMapper;
        this.contracts = Map.copyOf(contracts);
    }

    public static StructuredOutputContractRegistry standard() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .build())
                .build();
        ObjectMapper strictMapper = new ObjectMapper(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12);
        Map<StructuredContractRef, StructuredOutputContract> contracts =
                new HashMap<>();
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION,
                        "goal.provider-draft.v1"),
                "goal_provider_draft",
                "model-contracts/goal.provider-draft.v1.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION,
                        "goal.provider-draft.v2"),
                "goal_provider_draft_v2",
                "model-contracts/goal.provider-draft.v2.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION,
                        "goal.provider-draft.v3"),
                "goal_provider_draft_v3",
                "model-contracts/goal.provider-draft.v3.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION, "goal.proposal.v5"),
                "goal_proposal", "model-contracts/goal.proposal.v5.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v2"),
                "general_provider_draft",
                "model-contracts/general.provider-draft.v2.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v3"),
                "general_provider_draft_v3",
                "model-contracts/general.provider-draft.v3.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v4"),
                "general_provider_draft_v4",
                "model-contracts/general.provider-draft.v4.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2"),
                "general_draft", "model-contracts/general.draft.v2.schema.json");
        addApprovedContract(contracts, strictMapper, schemaRegistry,
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v3"),
                "general_draft_v3", "model-contracts/general.draft.v3.schema.json");
        return new StructuredOutputContractRegistry(strictMapper, contracts);
    }

    public StructuredOutputContract resolve(StructuredContractRef ref) {
        StructuredOutputContract contract = contracts.get(
                java.util.Objects.requireNonNull(ref, "ref"));
        if (contract == null) {
            throw new IllegalArgumentException("structured output contract is not approved");
        }
        return contract;
    }

    public StructurallyValidatedOutput validate(StructuredContractRef ref, String payload) {
        return validate(ref, payload,
                StructuredOutputSchemaFailureClassifier.generic());
    }

    public StructurallyValidatedOutput validate(
            StructuredContractRef ref, String payload,
            StructuredOutputSchemaFailureClassifier failureClassifier) {
        StructuredOutputContract contract = resolve(ref);
        StructuredOutputSchemaFailureClassifier requiredClassifier =
                java.util.Objects.requireNonNull(
                        failureClassifier, "failureClassifier");
        if (payload == null || payload.isBlank()) {
            throw new StructuredOutputValidationException(
                    StructuredOutputValidationException.Reason.INVALID_JSON);
        }
        if (payload.length() > MAX_PAYLOAD_CHARACTERS) {
            throw new StructuredOutputValidationException(
                    StructuredOutputValidationException.Reason.OUTPUT_TOO_LARGE);
        }
        JsonNode tree;
        try {
            tree = strictMapper.readTree(payload);
        } catch (StreamConstraintsException excessiveNesting) {
            throw resourceLimit("OUTPUT_TOO_LARGE_RESOURCE_NESTING_DEPTH");
        } catch (Exception invalid) {
            throw new StructuredOutputValidationException(
                    StructuredOutputValidationException.Reason.INVALID_JSON);
        }
        if (tree == null || !tree.isObject()) {
            throw new StructuredOutputValidationException(
                    StructuredOutputValidationException.Reason.INVALID_JSON);
        }
        return validateTree(contract, tree, requiredClassifier);
    }

    public StructurallyValidatedOutput validateTree(
            StructuredContractRef ref, JsonNode tree) {
        StructuredOutputContract contract = resolve(ref);
        if (tree == null || !tree.isObject()) {
            throw new StructuredOutputValidationException(
                    StructuredOutputValidationException.Reason.INVALID_JSON);
        }
        return validateTree(contract, tree,
                StructuredOutputSchemaFailureClassifier.generic());
    }

    private StructurallyValidatedOutput validateTree(
            StructuredOutputContract contract, JsonNode tree,
            StructuredOutputSchemaFailureClassifier failureClassifier) {
        enforceResourceLimits(tree);
        List<Error> errors = contract.validator().validate(tree);
        if (!errors.isEmpty()) {
            StructuredOutputValidationException.Reason reason = classify(errors);
            StructuredOutputValidationException genericFailure =
                    new StructuredOutputValidationException(
                            reason, diagnosticReason(
                                    reason, deepestErrors(errors), tree));
            throw java.util.Objects.requireNonNull(
                    failureClassifier.classify(
                            tree.deepCopy(), genericFailure),
                    "classified schema failure");
        }
        return new StructurallyValidatedOutput(
                contract.ref(), contract.contractFingerprint(), tree);
    }

    /** Schema 之前的固定资源护栏；开放字段也不能绕过深度与数组总量上限。 */
    private static void enforceResourceLimits(JsonNode root) {
        ArrayDeque<NodeAtDepth> pending = new ArrayDeque<>();
        pending.addLast(new NodeAtDepth(root, 1));
        int totalArrayElements = 0;
        while (!pending.isEmpty()) {
            NodeAtDepth current = pending.removeFirst();
            if (current.depth() > MAX_NESTING_DEPTH) {
                throw resourceLimit("OUTPUT_TOO_LARGE_RESOURCE_NESTING_DEPTH");
            }
            JsonNode node = current.node();
            if (node.isArray()) {
                if (node.size() > MAX_TOTAL_ARRAY_ELEMENTS - totalArrayElements) {
                    throw resourceLimit("OUTPUT_TOO_LARGE_RESOURCE_ARRAY_ELEMENTS");
                }
                totalArrayElements += node.size();
            }
            if (node.isContainerNode()) {
                node.elements().forEachRemaining(child -> pending.addLast(
                        new NodeAtDepth(child, current.depth() + 1)));
            }
        }
    }

    private static StructuredOutputValidationException resourceLimit(
            String diagnosticReason) {
        return new StructuredOutputValidationException(
                StructuredOutputValidationException.Reason.OUTPUT_TOO_LARGE,
                diagnosticReason);
    }

    private record NodeAtDepth(JsonNode node, int depth) { }

    private static StructuredOutputValidationException.Reason classify(
            List<Error> errors) {
        List<Error> deepestErrors = deepestErrors(errors);
        if (hasKeyword(deepestErrors, "additionalProperties")) {
            return StructuredOutputValidationException.Reason.UNKNOWN_FIELD;
        }
        if (hasKeyword(deepestErrors, "const") || hasKeyword(deepestErrors, "enum")) {
            return StructuredOutputValidationException.Reason.FIELD_VALUE_INVALID;
        }
        if (hasKeyword(deepestErrors, "minItems")
                || hasKeyword(deepestErrors, "maxItems")
                || hasKeyword(deepestErrors, "uniqueItems")) {
            return StructuredOutputValidationException.Reason.ARRAY_CONSTRAINT_INVALID;
        }
        if (hasKeyword(deepestErrors, "minLength")
                || hasKeyword(deepestErrors, "maxLength")
                || hasKeyword(deepestErrors, "pattern")) {
            return StructuredOutputValidationException.Reason.STRING_CONSTRAINT_INVALID;
        }
        if (hasKeyword(deepestErrors, "type")) {
            return StructuredOutputValidationException.Reason.FIELD_TYPE_INVALID;
        }
        if (hasKeyword(deepestErrors, "minimum")
                || hasKeyword(deepestErrors, "maximum")) {
            return StructuredOutputValidationException.Reason.NUMBER_CONSTRAINT_INVALID;
        }
        if (hasKeyword(deepestErrors, "required")) {
            return StructuredOutputValidationException.Reason.MISSING_REQUIRED_FIELD;
        }
        if (hasKeyword(deepestErrors, "oneOf")) {
            return StructuredOutputValidationException.Reason.VARIANT_SHAPE_INVALID;
        }
        return StructuredOutputValidationException.Reason.LOCAL_SCHEMA_REJECTED;
    }

    private static List<Error> deepestErrors(List<Error> errors) {
        int maxDepth = errors.stream()
                .mapToInt(error -> error.getInstanceLocation().toString().length())
                .max().orElse(0);
        return errors.stream()
                .filter(error -> error.getInstanceLocation().toString().length() == maxDepth)
                .toList();
    }

    private static boolean hasKeyword(List<Error> errors, String keyword) {
        return errors.stream().anyMatch(error -> keyword.equals(error.getKeyword()));
    }

    private static String diagnosticReason(
            StructuredOutputValidationException.Reason reason,
            List<Error> errors, JsonNode tree) {
        String safeField = safeSchemaField(errors);
        if (reason == StructuredOutputValidationException.Reason
                .STRING_CONSTRAINT_INVALID && safeField != null) {
            String suffix = safeStringConstraintSuffix(tree, safeField);
            if (suffix != null) {
                return reason.name() + "_" + camelToUpperSnake(safeField)
                        + "_" + suffix;
            }
        }
        if (reason == StructuredOutputValidationException.Reason
                .ARRAY_CONSTRAINT_INVALID && safeField != null) {
            if (hasKeyword(errors, "minItems")) {
                return reason.name() + "_" + camelToUpperSnake(safeField)
                        + "_MIN_ITEMS";
            }
            if (hasKeyword(errors, "maxItems")) {
                return reason.name() + "_" + camelToUpperSnake(safeField)
                        + "_MAX_ITEMS";
            }
            if (hasKeyword(errors, "uniqueItems")) {
                return reason.name() + "_" + camelToUpperSnake(safeField)
                        + "_UNIQUE_ITEMS";
            }
        }
        if (reason != StructuredOutputValidationException.Reason.FIELD_TYPE_INVALID) {
            return reason.name();
        }
        if (safeField != null) {
            return reason.name() + "_" + camelToUpperSnake(safeField);
        }
        return reason.name();
    }

    private static String safeSchemaField(List<Error> errors) {
        for (Error error : errors) {
            String location = error.getInstanceLocation().toString();
            for (String field : SAFE_SCHEMA_FIELDS) {
                if (location.endsWith("/" + field)
                        || location.endsWith("." + field)
                        || location.equals(field)
                        || location.contains("/" + field + "/")) {
                    return field;
                }
            }
        }
        return null;
    }

    private static String safeStringConstraintSuffix(String value) {
        if (value.startsWith("。") || value.contains("。。")) {
            return "EMPTY_SEGMENT";
        }
        if (value.contains("；") || value.contains(";")) {
            return "SEMICOLON";
        }
        if (value.contains("？") || value.contains("?")) {
            return "QUESTION_MARK";
        }
        if (value.contains("！") || value.contains("!")) {
            return "EXCLAMATION_MARK";
        }
        if (value.contains(".")) {
            return "ASCII_PERIOD";
        }
        if (!value.matches(".*\\p{IsHan}.*")) {
            return "LANGUAGE";
        }
        String[] segments = value.split("。", -1);
        int length = value.endsWith("。") ? segments.length - 1 : segments.length;
        for (int index = 0; index < length; index++) {
            if (!segments[index].matches(".*\\p{IsHan}.*")) {
                return "SEGMENT_LANGUAGE";
            }
        }
        return null;
    }

    private static String safeStringConstraintSuffix(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (value.isTextual()) {
            return safeStringConstraintSuffix(value.textValue());
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item.isTextual()) {
                    String suffix = safeStringConstraintSuffix(item.textValue());
                    if (suffix != null) {
                        return suffix;
                    }
                }
            }
        }
        return null;
    }

    private static String safeStringConstraintSuffix(
            JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = node.get(field);
            String directSuffix = safeStringConstraintSuffix(direct);
            if (directSuffix != null) {
                return directSuffix;
            }
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                String suffix = safeStringConstraintSuffix(children.next(), field);
                if (suffix != null) {
                    return suffix;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String suffix = safeStringConstraintSuffix(item, field);
                if (suffix != null) {
                    return suffix;
                }
            }
        }
        return null;
    }

    private static String camelToUpperSnake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(
                java.util.Locale.ROOT);
    }

    private static void addApprovedContract(
            Map<StructuredContractRef, StructuredOutputContract> contracts,
            ObjectMapper mapper,
            SchemaRegistry schemaRegistry,
            StructuredContractRef ref,
            String outputName,
            String resourceName) {
        JsonNode schemaNode = readResource(mapper, resourceName);
        Schema validator = schemaRegistry.getSchema(schemaNode);
        String fingerprint = sha256(canonicalBytes(mapper, schemaNode));
        StructuredOutputContract previous = contracts.put(ref,
                new StructuredOutputContract(
                        ref, outputName, schemaNode, fingerprint, validator));
        if (previous != null) {
            throw new IllegalStateException("duplicate structured output contract");
        }
    }

    private static JsonNode readResource(ObjectMapper mapper, String resourceName) {
        try (InputStream input = StructuredOutputContractRegistry.class
                .getResourceAsStream("/" + resourceName)) {
            if (input == null) {
                throw new IllegalStateException("structured output contract resource is missing");
            }
            JsonNode schema = mapper.readTree(input);
            if (schema == null || !schema.isObject()) {
                throw new IllegalStateException("structured output contract resource is invalid");
            }
            return schema;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("structured output contract resource is invalid", exception);
        }
    }

    private static byte[] canonicalBytes(ObjectMapper mapper, JsonNode node) {
        try {
            return mapper.writeValueAsBytes(canonicalize(mapper, node));
        } catch (Exception exception) {
            throw new IllegalStateException("structured output contract cannot be canonicalized", exception);
        }
    }

    private static JsonNode canonicalize(ObjectMapper mapper, JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            Iterator<String> iterator = node.fieldNames();
            iterator.forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                result.set(name, canonicalize(mapper, node.get(name)));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            for (JsonNode item : node) {
                result.add(canonicalize(mapper, item));
            }
            return result;
        }
        return node.deepCopy();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
