package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class PortfolioSnapshotJsonReader {

    private static final Set<String> BUNDLE_FIELDS = Set.of(
            "schemaVersion", "contentVersion", "owner", "projects",
            "cases", "claims", "evidence", "claimEvidenceLinks", "timelineEvents",
            "questionPresets");
    private static final Set<String> LEGACY_FIELDS = Set.of(
            "schemaVersion", "contentVersion", "publishedAt", "owner", "projects",
            "claims", "evidence", "claimEvidenceLinks", "questions", "timeline");

    private final ObjectMapper objectMapper;
    private final ObjectMapper strictMapper;

    public PortfolioSnapshotJsonReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.strictMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public PortfolioSnapshot readBundle(byte[] bytes) {
        try {
            JsonNode parsed = objectMapper.readTree(bytes);
            require(parsed instanceof ObjectNode,
                    "portfolio.json must contain a JSON object");
            ObjectNode root = (ObjectNode) parsed;
            String schemaVersion = requiredText(root, "schemaVersion");
            if ("2.0".equals(schemaVersion)) {
                ArrayNode questionPresets = requiredArray(root, "questionPresets");
                ArrayNode timelineEvents = requiredArray(root, "timelineEvents");
                root.putArray("cases");
                normalizeArray(questionPresets, "caseIds");
                normalizeArray(timelineEvents, "caseIds");
            } else if ("3.0".equals(schemaVersion)) {
                JsonNode cases = root.get("cases");
                require(cases != null, "cases is required for schemaVersion 3.0");
                require(cases.isArray(), "cases is required and must be an array");
                requireNestedArrayPresent(
                        requiredArray(root, "questionPresets"), "caseIds");
                requireNestedArrayPresent(
                        requiredArray(root, "timelineEvents"), "caseIds");
            } else {
                throw invalid("unsupported schemaVersion: " + schemaVersion);
            }
            requireExactFields(root, BUNDLE_FIELDS);
            return strictMapper.treeToValue(root, PortfolioSnapshot.class);
        } catch (InvalidPortfolioSnapshotException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("unable to read portfolio.json: " + exception.getMessage(), exception);
        }
    }

    public PortfolioSnapshot readLegacyResource(byte[] bytes) {
        try {
            JsonNode parsed = objectMapper.readTree(bytes);
            require(parsed instanceof ObjectNode,
                    "legacy portfolio resource must contain a JSON object");
            ObjectNode root = (ObjectNode) parsed;
            String schemaVersion = requiredText(root, "schemaVersion");
            require("2.0".equals(schemaVersion),
                    "unsupported schemaVersion: " + schemaVersion);
            ArrayNode questions = requiredArray(root, "questions");
            ArrayNode timeline = requiredArray(root, "timeline");
            requireExactFields(
                    root,
                    LEGACY_FIELDS,
                    "legacy portfolio resource field set is not canonical");
            root.set("questionPresets", questions);
            root.remove("questions");
            root.set("timelineEvents", timeline);
            root.remove("timeline");
            root.putArray("cases");
            normalizeArray(questions, "caseIds");
            normalizeArray(timeline, "caseIds");
            return strictMapper.treeToValue(root, PortfolioSnapshot.class);
        } catch (InvalidPortfolioSnapshotException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid(
                    "unable to read legacy portfolio resource: " + exception.getMessage(),
                    exception);
        }
    }

    private String requiredText(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        require(value != null && value.isTextual() && !value.textValue().isBlank(),
                field + " is required");
        return value.textValue();
    }

    private ArrayNode requiredArray(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        require(value != null && value.isArray(),
                field + " is required and must be an array");
        return (ArrayNode) value;
    }

    private void normalizeArray(ArrayNode items, String field) {
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            require(item instanceof ObjectNode,
                    "array item at index " + index + " must be a JSON object");
            ((ObjectNode) item).putArray(field);
        }
    }

    private void requireNestedArrayPresent(ArrayNode items, String field) {
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            require(item instanceof ObjectNode,
                    "array item at index " + index + " must be a JSON object");
            JsonNode value = item.get(field);
            require(value != null && value.isArray(),
                    "items[" + index + "]." + field + " must be an array");
        }
    }

    private void requireExactFields(ObjectNode root, Set<String> expected) {
        requireExactFields(root, expected, "portfolio.json field set is not canonical");
    }

    private void requireExactFields(ObjectNode root, Set<String> expected, String message) {
        Set<String> actual = new HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), message);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private InvalidPortfolioSnapshotException invalid(String message) {
        return new InvalidPortfolioSnapshotException(message);
    }

    private InvalidPortfolioSnapshotException invalid(String message, Throwable cause) {
        return new InvalidPortfolioSnapshotException(message, cause);
    }
}
