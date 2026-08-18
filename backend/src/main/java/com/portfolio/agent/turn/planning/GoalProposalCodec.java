package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GoalProposalCodec {

    private static final int MAX_OUTPUT_CHARACTERS = 20000;
    private final ObjectMapper mapper;

    public GoalProposalCodec() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(factory);
    }

    public GoalInterpretationResult decode(String json, GoalInterpretationInput input) {
        if (json == null || json.isBlank() || json.length() > MAX_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException("goal proposal output must be non-empty and bounded");
        }
        JsonNode root = readStrict(json);
        requireObject(root, "root");
        String kind = requireText(root, "kind", 64);
        return switch (kind) {
            case "GOALS" -> decodeGoals(root, input);
            case "CLARIFICATION" -> decodeClarification(root, input);
            case "CONVERSATIONAL" -> decodeConversational(root);
            default -> throw new IllegalArgumentException("unsupported goal interpretation kind");
        };
    }

    private GoalInterpretationResult decodeGoals(JsonNode root, GoalInterpretationInput input) {
        assertFields(root, Set.of("kind", "goals"), Set.of("kind", "goals"), "root");
        JsonNode goalsNode = requireArray(root, "goals");
        if (goalsNode.isEmpty() || goalsNode.size() > 6) {
            throw new IllegalArgumentException("goals must contain between one and six items");
        }
        List<UserGoalProposal.ProposedGoal> goals = new ArrayList<>();
        for (int index = 0; index < goalsNode.size(); index++) {
            goals.add(decodeGoal(goalsNode.get(index), input, "goals[" + index + "]"));
        }
        return GoalInterpretationResult.goals(new UserGoalProposal(List.copyOf(goals)));
    }

    private UserGoalProposal.ProposedGoal decodeGoal(
            JsonNode node, GoalInterpretationInput input, String path) {
        requireObject(node, path);
        assertFields(node, Set.of(
                        "goalKey", "goalKind", "inputAnchor", "subjectCandidates",
                        "requestedOutputs", "knowledgeRequirement", "parameters"),
                Set.of("goalKey", "goalKind", "inputAnchor", "subjectCandidates",
                        "requestedOutputs", "knowledgeRequirement", "parameters"), path);
        GoalKind goalKind = enumValue(
                GoalKind.class, requireText(node, "goalKind", 64), path + ".goalKind");
        if (!input.getAllowedGoalKinds().contains(goalKind)) {
            throw new IllegalArgumentException(path + ".goalKind is not allowed");
        }
        UserGoalProposal.InputAnchor inputAnchor = decodeAnchor(
                node.get("inputAnchor"), input.getUserText(), path + ".inputAnchor");
        List<GoalSubjectReference> subjects = decodeSubjects(
                requireArray(node, "subjectCandidates"), input, path + ".subjectCandidates");
        Set<GoalRequestedOutput> outputs = decodeEnumSet(
                requireArray(node, "requestedOutputs"), GoalRequestedOutput.class,
                path + ".requestedOutputs", false);
        GoalKnowledgeRequirement knowledge = enumValue(
                GoalKnowledgeRequirement.class,
                requireText(node, "knowledgeRequirement", 64),
                path + ".knowledgeRequirement");
        UserGoalProposal.GoalParameters parameters = decodeParameters(
                node.get("parameters"), goalKind, input.getUserText(), path + ".parameters");
        return new UserGoalProposal.ProposedGoal(
                requireText(node, "goalKey", 64), goalKind, inputAnchor,
                subjects, outputs, knowledge, parameters);
    }

    private List<GoalSubjectReference> decodeSubjects(
            JsonNode array,
            GoalInterpretationInput input,
            String path) {
        if (array.size() > 5) {
            throw new IllegalArgumentException(path + " must contain at most five items");
        }
        List<GoalSubjectReference> subjects = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            JsonNode node = array.get(index);
            String itemPath = path + "[" + index + "]";
            requireObject(node, itemPath);
            assertFields(node, Set.of("kind", "reference", "basis", "anchor"),
                    Set.of("kind", "reference", "basis"), itemPath);
            GoalSubjectReference.Kind kind = enumValue(
                    GoalSubjectReference.Kind.class, requireText(node, "kind", 64), itemPath + ".kind");
            String reference = requireText(node, "reference", 128);
            GoalSubjectReference.Basis basis = enumValue(
                    GoalSubjectReference.Basis.class,
                    requireText(node, "basis", 64), itemPath + ".basis");
            UserGoalProposal.InputAnchor anchor = node.has("anchor")
                    ? decodeAnchor(node.get("anchor"), input.getUserText(), itemPath + ".anchor")
                    : null;
            if (kind != GoalSubjectReference.Kind.RESULT
                    && !input.containsPublicSubject(kind, reference)) {
                throw new IllegalArgumentException(itemPath + " references a non-public subject");
            }
            String identity = kind.name() + ':' + reference;
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(path + " contains duplicate subjects");
            }
            subjects.add(new GoalSubjectReference(kind, reference, basis, anchor));
        }
        return List.copyOf(subjects);
    }

    private UserGoalProposal.GoalParameters decodeParameters(
            JsonNode node,
            GoalKind goalKind,
            String input,
            String path) {
        requireObject(node, path);
        String parameterKind = requireText(node, "kind", 64);
        if (!parameterKind.equals(goalKind.name())) {
            throw new IllegalArgumentException(path + ".kind must match goalKind");
        }
        return switch (goalKind) {
            case PORTFOLIO_FACT -> {
                assertFields(node, Set.of("kind", "facets"), Set.of("kind", "facets"), path);
                yield new UserGoalProposal.PortfolioFactParameters(decodeEnumSet(
                        requireArray(node, "facets"), UserGoalProposal.Facet.class,
                        path + ".facets", false));
            }
            case PORTFOLIO_COMPARE -> {
                assertFields(node, Set.of("kind", "dimensions"),
                        Set.of("kind", "dimensions"), path);
                yield new UserGoalProposal.PortfolioCompareParameters(
                        decodeClosedNames(requireArray(node, "dimensions"), path + ".dimensions", false));
            }
            case PORTFOLIO_RECOMMEND -> {
                assertFields(node, Set.of("kind", "requestedSize", "constraints"),
                        Set.of("kind", "requestedSize", "constraints"), path);
                yield new UserGoalProposal.PortfolioRecommendationParameters(
                        requireInt(node, "requestedSize"),
                        decodeClosedNames(requireArray(node, "constraints"), path + ".constraints", true));
            }
            case PORTFOLIO_REFINE_RECOMMENDATION -> {
                assertFields(node, Set.of("kind", "constraints"),
                        Set.of("kind", "constraints"), path);
                yield new UserGoalProposal.PortfolioRefineParameters(
                        decodeClosedNames(requireArray(node, "constraints"), path + ".constraints", false));
            }
            case GENERAL_EXPLANATION -> {
                assertFields(node, Set.of("kind", "topicAnchor", "depth"),
                        Set.of("kind", "topicAnchor", "depth"), path);
                yield new UserGoalProposal.GeneralExplanationParameters(
                        decodeAnchor(node.get("topicAnchor"), input, path + ".topicAnchor"),
                        enumValue(UserGoalProposal.Depth.class,
                                requireText(node, "depth", 64), path + ".depth"));
            }
            case GENERAL_COMPARISON -> {
                assertFields(node, Set.of("kind", "subjectAnchors", "dimensions"),
                        Set.of("kind", "subjectAnchors", "dimensions"), path);
                JsonNode anchors = requireArray(node, "subjectAnchors");
                List<UserGoalProposal.InputAnchor> decoded = new ArrayList<>();
                for (int index = 0; index < anchors.size(); index++) {
                    decoded.add(decodeAnchor(anchors.get(index), input,
                            path + ".subjectAnchors[" + index + "]"));
                }
                yield new UserGoalProposal.GeneralComparisonParameters(
                        List.copyOf(decoded), decodeClosedNames(
                                requireArray(node, "dimensions"), path + ".dimensions", false));
            }
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> {
                assertFields(node, Set.of("kind", "conceptAnchor", "portfolioFacet"),
                        Set.of("kind", "conceptAnchor", "portfolioFacet"), path);
                yield new UserGoalProposal.ApplyConceptParameters(
                        decodeAnchor(node.get("conceptAnchor"), input, path + ".conceptAnchor"),
                        enumValue(UserGoalProposal.Facet.class,
                                requireText(node, "portfolioFacet", 64), path + ".portfolioFacet"));
            }
        };
    }

    private GoalInterpretationResult decodeClarification(
            JsonNode root, GoalInterpretationInput input) {
        assertFields(root, Set.of("kind", "clarification"),
                Set.of("kind", "clarification"), "root");
        JsonNode node = root.get("clarification");
        requireObject(node, "clarification");
        assertFields(node, Set.of("field", "prompt", "inputAnchor"),
                Set.of("field", "prompt", "inputAnchor"), "clarification");
        return GoalInterpretationResult.clarification(new ClarificationProposal(
                enumValue(ClarificationProposal.Field.class,
                        requireText(node, "field", 64), "clarification.field"),
                requireText(node, "prompt", 400),
                decodeAnchor(node.get("inputAnchor"), input.getUserText(),
                        "clarification.inputAnchor")));
    }

    private GoalInterpretationResult decodeConversational(JsonNode root) {
        assertFields(root, Set.of("kind", "message"), Set.of("kind", "message"), "root");
        return GoalInterpretationResult.conversational(requireText(root, "message", 400));
    }

    private UserGoalProposal.InputAnchor decodeAnchor(
            JsonNode node, String input, String path) {
        requireObject(node, path);
        assertFields(node, Set.of("text", "start"), Set.of("text", "start"), path);
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(
                requireText(node, "text", 256), requireInt(node, "start"));
        anchor.requireMatches(input);
        return anchor;
    }

    private Set<String> decodeClosedNames(JsonNode array, String path, boolean allowEmpty) {
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String value = array.get(index).asText(null);
            if (value == null || !value.matches("[A-Z_]{1,64}") || !values.add(value)) {
                throw new IllegalArgumentException(path + " contains invalid or duplicate values");
            }
        }
        if (!allowEmpty && values.isEmpty()) {
            throw new IllegalArgumentException(path + " must not be empty");
        }
        return Set.copyOf(values);
    }

    private <E extends Enum<E>> Set<E> decodeEnumSet(
            JsonNode array, Class<E> type, String path, boolean allowEmpty) {
        Set<E> values = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            E value = enumValue(type, array.get(index).asText(null), path + '[' + index + ']');
            if (!values.add(value)) {
                throw new IllegalArgumentException(path + " contains duplicate values");
            }
        }
        if (!allowEmpty && values.isEmpty()) {
            throw new IllegalArgumentException(path + " must not be empty");
        }
        return Set.copyOf(values);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
        if (value == null) throw new IllegalArgumentException(path + " is required");
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(path + " is not supported", failure);
        }
    }

    private JsonNode readStrict(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("invalid goal proposal JSON", failure);
        }
    }

    private void assertFields(
            JsonNode node, Set<String> allowed, Set<String> required, String path) {
        Iterator<String> names = node.fieldNames();
        Set<String> actual = new HashSet<>();
        while (names.hasNext()) {
            String name = names.next();
            actual.add(name);
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException(path + " contains unknown field: " + name);
            }
        }
        if (!actual.containsAll(required)) {
            throw new IllegalArgumentException(path + " is missing required fields");
        }
    }

    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
    }

    private JsonNode requireArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private String requireText(JsonNode node, String field, int maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()
                || value.asText().isBlank() || value.asText().length() > maximum) {
            throw new IllegalArgumentException(field + " must be non-empty and bounded");
        }
        return value.asText();
    }

    private int requireInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.intValue();
    }
}
