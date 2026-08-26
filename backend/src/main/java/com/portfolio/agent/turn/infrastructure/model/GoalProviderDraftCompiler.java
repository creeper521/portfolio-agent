package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputCompiler;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.SemanticRouteProposal;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 把 Provider 友好的扁平 Goal Draft 确定性编译为 canonical goal.proposal.v5。
 *
 * <p>Provider 只负责语义选择；goalKey、锚点位置、输出类别、知识需求、参数
 * discriminant 与 canonical null 均由服务端机械派生。编译器不修复、不重试、
 * 不补猜缺失的语义字段。</p>
 */
public final class GoalProviderDraftCompiler implements StructuredOutputCompiler {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "decision", "message", "candidateKey", "goal",
            "clarification", "recentReference");
    private static final Set<String> GOAL_FIELDS = Set.of(
            "goalKind", "inputText", "subjects", "facets", "depth",
            "dimensions", "requestedSize", "constraints", "topicText",
            "subjectTexts", "conceptText", "portfolioFacet");
    private static final Set<String> SUBJECT_FIELDS = Set.of(
            "kind", "reference", "inputText");
    private static final Set<String> CLARIFICATION_FIELDS = Set.of(
            "field", "prompt", "goal");
    private static final Set<String> RECENT_REFERENCE_FIELDS = Set.of(
            "goalId", "sectionId");
    private static final Set<String> PORTFOLIO_DIMENSIONS = Set.of(
            "ARCHITECTURE", "IMPLEMENTATION", "OUTCOME", "RISKS", "VERIFICATION");
    private static final Set<String> FACETS = Set.of(
            "OVERVIEW", "BACKGROUND", "RESPONSIBILITY", "SOLUTION",
            "VERIFICATION", "STATUS");
    private static final Set<String> DEPTHS = Set.of(
            "CONCISE", "STANDARD", "DETAILED");

    private final GoalInterpretationInput input;

    public GoalProviderDraftCompiler(GoalInterpretationInput input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    @Override
    public String profileVersion() {
        return com.portfolio.agent.infrastructure.model.structured.OperationBinding
                .GOAL_DRAFT_OUTPUT_COMPILER_VERSION;
    }

    @Override
    public JsonNode compile(JsonNode draft) {
        requireObject(draft);
        requireOnly(draft, ROOT_FIELDS);
        String decision = text(draft, "decision");
        return switch (decision) {
            case "CONVERSATIONAL" -> conversational(draft);
            case "STANDARD_GOAL", "CONTINUE_CURRENT_PROJECT" ->
                    goalRoute(draft, decision);
            case "ENTER_RECOMMENDED_RESULT", "SWITCH_PROJECT" ->
                    candidateRoute(draft, decision);
            case "START_NEW_TOPIC", "REENTER_PROJECT" -> emptyRoute(draft, decision);
            case "NEEDS_CLARIFICATION" -> clarificationRoute(draft);
            default -> throw fail(
                    StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        };
    }

    private JsonNode conversational(JsonNode draft) {
        requirePresentOnly(draft, Set.of("decision", "message"));
        ObjectNode root = JSON.objectNode();
        root.put("kind", "CONVERSATIONAL");
        root.put("message", text(draft, "message"));
        return root;
    }

    private JsonNode goalRoute(JsonNode draft, String route) {
        Set<String> allowed = "STANDARD_GOAL".equals(route)
                ? Set.of("decision", "goal", "recentReference")
                : Set.of("decision", "goal");
        requirePresentOnly(draft, allowed);
        if (!input.getAllowedRoutes().contains(route(route))) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        ObjectNode root = semanticRoot(route);
        root.set("goal", compileGoal(object(draft, "goal")));
        if ("STANDARD_GOAL".equals(route) && draft.has("recentReference")) {
            root.set("recentReference", compileRecentReference(
                    object(draft, "recentReference")));
        }
        return root;
    }

    private JsonNode candidateRoute(JsonNode draft, String route) {
        requirePresentOnly(draft, Set.of("decision", "candidateKey"));
        if (!input.getAllowedRoutes().contains(route(route))) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        String candidateKey = text(draft, "candidateKey");
        boolean supplied = input.getRouteCandidates().stream().anyMatch(
                candidate -> candidate.getCandidateKey().equals(candidateKey));
        if (!supplied) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        ObjectNode root = semanticRoot(route);
        root.put("candidateKey", candidateKey);
        return root;
    }

    private JsonNode emptyRoute(JsonNode draft, String route) {
        requirePresentOnly(draft, Set.of("decision"));
        if (!input.getAllowedRoutes().contains(route(route))) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        return semanticRoot(route);
    }

    private JsonNode clarificationRoute(JsonNode draft) {
        requirePresentOnly(draft, Set.of("decision", "clarification"));
        if (!input.getAllowedRoutes().contains(
                SemanticRouteProposal.Route.NEEDS_CLARIFICATION)) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        ObjectNode root = semanticRoot("NEEDS_CLARIFICATION");
        if (!draft.has("clarification")) {
            if (!input.getRouteCandidates().isEmpty()
                    || input.getInterpretationMode()
                    == GoalInterpretationInput.InterpretationMode.DISCUSSION) {
                return root;
            }
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        if (!input.getRouteCandidates().isEmpty()
                || input.getInterpretationMode()
                == GoalInterpretationInput.InterpretationMode.DISCUSSION) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        }
        root.set("clarification", compileClarification(
                object(draft, "clarification")));
        return root;
    }

    private ObjectNode compileGoal(JsonNode goal) {
        requireOnly(goal, GOAL_FIELDS);
        GoalKind kind = goalKind(goal);
        if (!input.getAllowedGoalKinds().contains(kind)) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        ObjectNode canonical = JSON.objectNode();
        canonical.put("goalKey", goalKey(kind));
        canonical.put("goalKind", kind.name());
        canonical.set("inputAnchor", anchor(text(goal, "inputText")));
        validateCompleteGoalBranch(goal, kind);
        canonical.set("subjectCandidates", subjects(goal));
        ArrayNode outputs = outputs(goal, kind);
        canonical.set("requestedOutputs", outputs);
        canonical.put("knowledgeRequirement", knowledge(kind));
        canonical.set("parameters", parameters(goal, kind));
        return canonical;
    }

    private ObjectNode compileClarification(JsonNode clarification) {
        requireOnly(clarification, CLARIFICATION_FIELDS);
        String field = text(clarification, "field");
        if (!Set.of("SUBJECT", "OUTPUT", "REQUESTED_SIZE", "CONSTRAINT")
                .contains(field)) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        JsonNode goal = object(clarification, "goal");
        requireOnly(goal, GOAL_FIELDS);
        GoalKind kind = goalKind(goal);
        if (!kind.name().startsWith("PORTFOLIO_")) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        if ((kind == GoalKind.PORTFOLIO_FACT
                || kind == GoalKind.PORTFOLIO_COMPARE) && !"SUBJECT".equals(field)
                || kind == GoalKind.PORTFOLIO_RECOMMEND
                && !"REQUESTED_SIZE".equals(field)) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        }
        validatePartialGoalBranch(goal, kind, field);
        ObjectNode value = JSON.objectNode();
        value.put("field", field);
        value.put("prompt", text(clarification, "prompt"));
        value.set("blockedGoal", blockedGoal(goal, kind, field));
        return value;
    }

    private ObjectNode blockedGoal(JsonNode goal, GoalKind kind, String field) {
        ObjectNode blocked = JSON.objectNode();
        blocked.put("goalKind", kind.name());
        blocked.set("subjects", blockedSubjects(goal));
        blocked.set("requestedOutputs", partialOutputs(goal, kind, field));
        blocked.set("facets", optionalClosedArray(goal, "facets", FACETS));
        blocked.set("dimensions", optionalDimensions(goal, kind));
        if (goal.has("requestedSize")) {
            blocked.put("requestedSize", integer(goal, "requestedSize", 1, 5));
        } else {
            blocked.putNull("requestedSize");
        }
        blocked.set("constraints", optionalConstraints(goal));
        blocked.put("portfolioDepth", optionalClosedText(
                goal, "depth", DEPTHS, "STANDARD"));
        blocked.put("unresolvedField", field);
        blocked.putArray("askedFields").add(field);
        blocked.putArray("remainingFields");
        blocked.put("depth", 1);
        return blocked;
    }

    private ObjectNode compileRecentReference(JsonNode node) {
        requireOnly(node, RECENT_REFERENCE_FIELDS);
        ObjectNode reference = JSON.objectNode();
        reference.put("goalId", text(node, "goalId"));
        if (node.has("sectionId")) reference.put("sectionId", text(node, "sectionId"));
        else reference.putNull("sectionId");
        return reference;
    }

    private ObjectNode parameters(JsonNode goal, GoalKind kind) {
        ObjectNode parameters = JSON.objectNode();
        parameters.put("kind", kind.name());
        switch (kind) {
            case PORTFOLIO_FACT -> {
                parameters.set("facets", requiredClosedArray(goal, "facets", FACETS));
                parameters.put("depth", closedText(goal, "depth", DEPTHS));
            }
            case PORTFOLIO_COMPARE -> parameters.set(
                    "dimensions", requiredClosedArray(
                            goal, "dimensions", PORTFOLIO_DIMENSIONS));
            case PORTFOLIO_RECOMMEND -> {
                parameters.put("requestedSize", integer(goal, "requestedSize", 1, 5));
                parameters.set("constraints", requiredConstraints(goal));
            }
            case GENERAL_EXPLANATION -> {
                parameters.set("topicAnchor", anchor(text(goal, "topicText")));
                parameters.put("depth", closedText(goal, "depth", DEPTHS));
            }
            case GENERAL_COMPARISON -> {
                parameters.set("subjectAnchors", anchors(goal, "subjectTexts"));
                parameters.set("dimensions", requiredClosedNamesArray(
                        goal, "dimensions"));
            }
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> {
                parameters.set("conceptAnchor", anchor(text(goal, "conceptText")));
                parameters.put("portfolioFacet", closedText(
                        goal, "portfolioFacet", FACETS));
                parameters.put("depth", closedText(goal, "depth", DEPTHS));
            }
        }
        return parameters;
    }

    private void validateCompleteGoalBranch(JsonNode goal, GoalKind kind) {
        Set<String> allowed;
        Set<String> required;
        switch (kind) {
            case PORTFOLIO_FACT -> {
                allowed = Set.of("goalKind", "inputText", "subjects", "facets", "depth");
                required = Set.of("goalKind", "inputText", "facets", "depth");
            }
            case PORTFOLIO_COMPARE -> {
                allowed = Set.of("goalKind", "inputText", "subjects", "dimensions");
                required = allowed;
            }
            case PORTFOLIO_RECOMMEND -> {
                allowed = Set.of("goalKind", "inputText", "requestedSize", "constraints");
                required = allowed;
            }
            case GENERAL_EXPLANATION -> {
                allowed = Set.of("goalKind", "inputText", "topicText", "depth");
                required = allowed;
            }
            case GENERAL_COMPARISON -> {
                allowed = Set.of("goalKind", "inputText", "subjectTexts", "dimensions");
                required = allowed;
            }
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> {
                allowed = Set.of("goalKind", "inputText", "subjects", "conceptText",
                        "portfolioFacet", "depth");
                required = Set.of("goalKind", "inputText", "conceptText",
                        "portfolioFacet", "depth");
            }
            default -> throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_BRANCH_INVALID);
        }
        requireExact(goal, allowed, required);
        if (kind == GoalKind.PORTFOLIO_COMPARE
                && array(goal, "subjects").size() < 2) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
    }

    private void validatePartialGoalBranch(
            JsonNode goal, GoalKind kind, String unresolvedField) {
        Set<String> allowed;
        Set<String> required;
        switch (kind) {
            case PORTFOLIO_FACT -> {
                allowed = Set.of("goalKind", "inputText", "subjects", "facets", "depth");
                required = Set.of("goalKind", "inputText", "facets", "depth");
            }
            case PORTFOLIO_COMPARE -> {
                allowed = Set.of("goalKind", "inputText", "subjects", "dimensions");
                required = Set.of("goalKind", "inputText", "dimensions");
            }
            case PORTFOLIO_RECOMMEND -> {
                allowed = Set.of("goalKind", "inputText", "requestedSize", "constraints");
                required = Set.of("goalKind", "inputText", "constraints");
            }
            default -> throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_BRANCH_INVALID);
        }
        requireExact(goal, allowed, required);
        String unresolvedDraftField = switch (unresolvedField) {
            case "SUBJECT" -> "subjects";
            case "REQUESTED_SIZE" -> "requestedSize";
            case "OUTPUT" -> "facets";
            case "CONSTRAINT" -> "constraints";
            default -> throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_BRANCH_INVALID);
        };
        boolean partialComparisonSubject = kind == GoalKind.PORTFOLIO_COMPARE
                && "SUBJECT".equals(unresolvedField)
                && goal.has("subjects")
                && array(goal, "subjects").size() == 1;
        if (goal.has(unresolvedDraftField) && !partialComparisonSubject) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        }
    }

    private ArrayNode outputs(JsonNode goal, GoalKind kind) {
        ArrayNode outputs = JSON.arrayNode();
        switch (kind) {
            case PORTFOLIO_FACT -> requiredClosedArray(goal, "facets", FACETS)
                    .forEach(value -> outputs.add(value.textValue()));
            case PORTFOLIO_COMPARE, GENERAL_COMPARISON -> outputs.add("COMPARISON");
            case PORTFOLIO_RECOMMEND -> outputs.add("RECOMMENDATION");
            case GENERAL_EXPLANATION -> outputs.add("EXPLANATION");
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> outputs.add("RELATION");
        }
        return outputs;
    }

    private ArrayNode partialOutputs(JsonNode goal, GoalKind kind, String field) {
        if ("OUTPUT".equals(field)) return JSON.arrayNode();
        return switch (kind) {
            case PORTFOLIO_FACT -> goal.has("facets")
                    ? copyArray(goal.get("facets")) : JSON.arrayNode();
            case PORTFOLIO_COMPARE -> JSON.arrayNode().add("COMPARISON");
            case PORTFOLIO_RECOMMEND -> JSON.arrayNode().add("RECOMMENDATION");
            default -> throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_BRANCH_INVALID);
        };
    }

    private ArrayNode subjects(JsonNode goal) {
        if (!goal.has("subjects")) return JSON.arrayNode();
        JsonNode values = array(goal, "subjects");
        ArrayNode subjects = JSON.arrayNode();
        Set<String> identities = new HashSet<>();
        for (JsonNode subject : values) {
            requireObject(subject);
            requireOnly(subject, SUBJECT_FIELDS);
            String kindText = text(subject, "kind");
            String reference = text(subject, "reference");
            String inputText = text(subject, "inputText");
            GoalSubjectReference.Kind kind;
            try {
                kind = GoalSubjectReference.Kind.valueOf(kindText);
            } catch (IllegalArgumentException failure) {
                throw fail(StructuredOutputValidationException.Reason
                        .DRAFT_SUBJECT_OUTSIDE_PUBLIC_SCOPE);
            }
            GoalInterpretationInput.PublicSubjectDescriptor descriptor =
                    input.getPublicSubjects().stream().filter(candidate ->
                            candidate.getKind() == kind
                                    && candidate.getReference().equals(reference)
                                    && candidate.matchesAlias(inputText))
                            .findFirst().orElseThrow(() -> fail(
                                    StructuredOutputValidationException.Reason
                                            .DRAFT_SUBJECT_OUTSIDE_PUBLIC_SCOPE));
            if (!identities.add(descriptor.getKind().name() + ':'
                    + descriptor.getReference())) {
                throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
            }
            ObjectNode canonical = JSON.objectNode();
            canonical.put("kind", kind.name());
            canonical.put("reference", reference);
            canonical.put("basis", "EXPLICIT_INPUT");
            canonical.set("anchor", anchor(inputText));
            subjects.add(canonical);
        }
        return subjects;
    }

    private ArrayNode blockedSubjects(JsonNode goal) {
        ArrayNode canonical = subjects(goal);
        ArrayNode blocked = JSON.arrayNode();
        canonical.forEach(subject -> {
            ObjectNode value = JSON.objectNode();
            value.put("kind", subject.path("kind").textValue());
            value.put("reference", subject.path("reference").textValue());
            blocked.add(value);
        });
        return blocked;
    }

    private ArrayNode anchors(JsonNode goal, String field) {
        JsonNode values = array(goal, field);
        if (values.size() < 2) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        ArrayNode anchors = JSON.arrayNode();
        values.forEach(value -> {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
            }
            anchors.add(anchor(value.textValue()));
        });
        return anchors;
    }

    private ObjectNode anchor(String text) {
        int first = input.getUserText().indexOf(text);
        if (first < 0) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_ANCHOR_NOT_FOUND);
        }
        if (input.getUserText().indexOf(text, first + 1) >= 0) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_ANCHOR_AMBIGUOUS);
        }
        ObjectNode anchor = JSON.objectNode();
        anchor.put("text", text);
        anchor.put("start", first);
        return anchor;
    }

    private ArrayNode requiredConstraints(JsonNode goal) {
        if (!goal.has("constraints")) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        return validateConstraints(array(goal, "constraints"));
    }

    private ArrayNode optionalConstraints(JsonNode goal) {
        return goal.has("constraints")
                ? validateConstraints(array(goal, "constraints")) : JSON.arrayNode();
    }

    private ArrayNode validateConstraints(JsonNode values) {
        ArrayNode result = requiredTextValues(values, true);
        Set<String> supplied = new LinkedHashSet<>();
        result.forEach(value -> supplied.add(value.textValue()));
        try {
            input.requireAllowedRecommendationConstraints(supplied);
        } catch (IllegalArgumentException failure) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        return result;
    }

    private ArrayNode optionalDimensions(JsonNode goal, GoalKind kind) {
        if (!goal.has("dimensions")) return JSON.arrayNode();
        return kind == GoalKind.PORTFOLIO_COMPARE
                ? closedArray(array(goal, "dimensions"), PORTFOLIO_DIMENSIONS, true)
                : requiredTextValues(array(goal, "dimensions"), true);
    }

    private ArrayNode optionalClosedArray(
            JsonNode node, String field, Set<String> allowed) {
        return node.has(field)
                ? closedArray(array(node, field), allowed, true) : JSON.arrayNode();
    }

    private ArrayNode requiredClosedArray(
            JsonNode node, String field, Set<String> allowed) {
        return closedArray(array(node, field), allowed, false);
    }

    private ArrayNode closedArray(
            JsonNode values, Set<String> allowed, boolean allowEmpty) {
        ArrayNode result = requiredTextValues(values, allowEmpty);
        for (JsonNode value : result) {
            if (!allowed.contains(value.textValue())) {
                throw fail(StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
            }
        }
        return result;
    }

    private ArrayNode requiredTextArray(JsonNode node, String field) {
        return requiredTextValues(array(node, field), false);
    }

    private ArrayNode requiredClosedNamesArray(JsonNode node, String field) {
        ArrayNode values = requiredTextArray(node, field);
        for (JsonNode value : values) {
            if (!value.textValue().matches("[A-Z_]{1,64}")) {
                throw fail(StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
            }
        }
        return values;
    }

    private ArrayNode requiredTextValues(JsonNode values, boolean allowEmpty) {
        ArrayNode result = JSON.arrayNode();
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().isBlank()
                    || value.textValue().length() > 96
                    || !unique.add(value.textValue())) {
                throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
            }
            result.add(value.textValue());
        }
        if (!allowEmpty && result.isEmpty()) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        return result;
    }

    private ObjectNode semanticRoot(String route) {
        ObjectNode root = JSON.objectNode();
        root.put("kind", "SEMANTIC_ROUTE");
        root.put("route", route);
        root.putNull("candidateKey");
        root.putNull("goal");
        root.putNull("clarification");
        root.putNull("recentReference");
        return root;
    }

    private SemanticRouteProposal.Route route(String value) {
        try {
            return SemanticRouteProposal.Route.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
    }

    private GoalKind goalKind(JsonNode goal) {
        try {
            return GoalKind.valueOf(text(goal, "goalKind"));
        } catch (IllegalArgumentException failure) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
    }

    private String goalKey(GoalKind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private String knowledge(GoalKind kind) {
        return switch (kind) {
            case PORTFOLIO_FACT, PORTFOLIO_COMPARE, PORTFOLIO_RECOMMEND ->
                    "PUBLIC_PORTFOLIO_EVIDENCE";
            case GENERAL_EXPLANATION, GENERAL_COMPARISON ->
                    "STABLE_GENERAL_EXPLANATION";
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO ->
                    "PUBLIC_PORTFOLIO_EVIDENCE";
        };
    }

    private int integer(JsonNode node, String field, int minimum, int maximum) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        if (!value.isIntegralNumber()
                || value.intValue() < minimum || value.intValue() > maximum) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        return value.intValue();
    }

    private String closedText(JsonNode node, String field, Set<String> allowed) {
        String value = text(node, field);
        if (!allowed.contains(value)) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE);
        }
        return value;
    }

    private String optionalClosedText(
            JsonNode node, String field, Set<String> allowed, String absent) {
        return node.has(field) ? closedText(node, field, allowed) : absent;
    }

    private JsonNode object(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        requireObject(value);
        return value;
    }

    private JsonNode array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        if (!value.isArray()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw fail(StructuredOutputValidationException.Reason
                    .DRAFT_REQUIRED_FIELD_MISSING);
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw fail(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
        }
        return value.textValue();
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

    private void requireExact(
            JsonNode node, Set<String> allowed, Set<String> required) {
        requireOnly(node, allowed);
        for (String field : required) {
            if (!node.has(field)) {
                throw fail(StructuredOutputValidationException.Reason
                        .DRAFT_REQUIRED_FIELD_MISSING);
            }
        }
    }

    private void requirePresentOnly(JsonNode node, Set<String> allowed) {
        requireOnly(node, allowed);
        for (String field : ROOT_FIELDS) {
            if (!allowed.contains(field) && node.has(field)) {
                throw fail(StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
            }
        }
    }

    private ArrayNode copyArray(JsonNode values) {
        ArrayNode result = JSON.arrayNode();
        values.forEach(result::add);
        return result;
    }

    private StructuredOutputValidationException fail(
            StructuredOutputValidationException.Reason reason) {
        return new StructuredOutputValidationException(reason, reason.name());
    }
}
