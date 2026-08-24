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

    public static final String SCHEMA_VERSION = "goal.proposal.v5";
    private static final int MAX_OUTPUT_CHARACTERS = 20000;
    private final ObjectMapper mapper;
    private final ConversationalMessageValidator conversationalValidator;

    public GoalProposalCodec() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(factory);
        this.conversationalValidator = new ConversationalMessageValidator();
    }

    public GoalInterpretationResult decode(String json, GoalInterpretationInput input) {
        if (json == null || json.isBlank() || json.length() > MAX_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException("goal proposal output must be non-empty and bounded");
        }
        JsonNode root = readStrict(json);
        requireObject(root, "root");
        String kind = requireText(root, "kind", 64);
        return switch (kind) {
            case "SEMANTIC_ROUTE" -> decodeSemanticRoute(root, input);
            case "CONVERSATIONAL" -> decodeConversational(root, input);
            default -> throw new IllegalArgumentException("unsupported goal interpretation kind");
        };
    }

    private GoalInterpretationResult decodeSemanticRoute(
            JsonNode root, GoalInterpretationInput input) {
        Set<String> fields = Set.of(
                "kind", "route", "candidateKey", "goal", "clarification",
                "recentReference");
        assertFields(root, fields, fields, "root");
        SemanticRouteProposal.Route route = enumValue(
                SemanticRouteProposal.Route.class,
                requireText(root, "route", 64), "root.route");
        JsonNode candidateNode = root.get("candidateKey");
        JsonNode goalNode = root.get("goal");
        JsonNode clarificationNode = root.get("clarification");
        JsonNode recentReferenceNode = root.get("recentReference");
        SemanticRouteProposal proposal = switch (route) {
            case STANDARD_GOAL -> {
                requireNull(candidateNode, "root.candidateKey");
                requireNull(clarificationNode, "root.clarification");
                if (goalNode == null || goalNode.isNull()) {
                    throw new IllegalArgumentException(
                            "root.goal is required for STANDARD_GOAL");
                }
                UserGoalProposal goal = new UserGoalProposal(List.of(
                        decodeGoal(goalNode, input, "root.goal")));
                SemanticRouteProposal.RecentSemanticReference recentReference =
                        decodeRecentReference(recentReferenceNode, input);
                validateRecentSectionFacet(recentReference, goal, input);
                yield recentReference == null
                        ? SemanticRouteProposal.standardGoal(goal)
                        : SemanticRouteProposal.standardGoal(goal, recentReference);
            }
            case ENTER_RECOMMENDED_RESULT -> {
                requireNull(recentReferenceNode, "root.recentReference");
                requireNull(goalNode, "root.goal");
                requireNull(clarificationNode, "root.clarification");
                yield SemanticRouteProposal.enterRecommendedResult(
                        requireText(root, "candidateKey", 2));
            }
            case NEEDS_CLARIFICATION -> {
                requireNull(recentReferenceNode, "root.recentReference");
                requireNull(candidateNode, "root.candidateKey");
                requireNull(goalNode, "root.goal");
                if ((clarificationNode == null || clarificationNode.isNull())
                        && input.getInterpretationMode()
                        == GoalInterpretationInput.InterpretationMode.STANDARD
                        && input.getRouteCandidates().isEmpty()) {
                    throw new IllegalArgumentException(
                            "root.clarification is required");
                }
                yield clarificationNode == null || clarificationNode.isNull()
                        ? SemanticRouteProposal.needsClarification()
                        : SemanticRouteProposal.needsClarification(
                        decodeClarificationValue(clarificationNode, input));
            }
            case CONTINUE_CURRENT_PROJECT -> {
                requireNull(recentReferenceNode, "root.recentReference");
                requireDiscussion(input);
                requireNull(candidateNode, "root.candidateKey");
                requireNull(clarificationNode, "root.clarification");
                if (goalNode == null || goalNode.isNull()) {
                    throw new IllegalArgumentException(
                            "root.goal is required for discussion continuation");
                }
                yield SemanticRouteProposal.discussion(
                        route, null,
                        new UserGoalProposal(List.of(
                                decodeGoal(goalNode, input, "root.goal"))));
            }
            case SWITCH_PROJECT -> {
                requireNull(recentReferenceNode, "root.recentReference");
                requireDiscussion(input);
                requireNull(goalNode, "root.goal");
                requireNull(clarificationNode, "root.clarification");
                yield SemanticRouteProposal.discussion(
                        route, requireText(root, "candidateKey", 2), null);
            }
            case START_NEW_TOPIC, REENTER_PROJECT -> {
                requireNull(recentReferenceNode, "root.recentReference");
                requireDiscussion(input);
                requireNull(candidateNode, "root.candidateKey");
                requireNull(goalNode, "root.goal");
                requireNull(clarificationNode, "root.clarification");
                yield SemanticRouteProposal.stateRoute(route);
            }
        };
        return GoalInterpretationResult.semanticRoute(proposal);
    }

    private SemanticRouteProposal.RecentSemanticReference decodeRecentReference(
            JsonNode node, GoalInterpretationInput input) {
        if (node == null || node.isNull()) return null;
        requireObject(node, "root.recentReference");
        assertFields(node, Set.of("goalId", "sectionId"),
                Set.of("goalId", "sectionId"), "root.recentReference");
        String goalId = requireText(node, "goalId", 96);
        JsonNode sectionNode = node.get("sectionId");
        String sectionId = sectionNode.isNull()
                ? null : requireText(node, "sectionId", 96);
        SemanticRouteProposal.RecentSemanticReference reference =
                new SemanticRouteProposal.RecentSemanticReference(goalId, sectionId);
        if (input.recentPortfolioSubject(goalId, sectionId) == null) {
            throw new IllegalArgumentException(
                    "root.recentReference is outside typed recent state");
        }
        return reference;
    }

    private void validateRecentSectionFacet(
            SemanticRouteProposal.RecentSemanticReference reference,
            UserGoalProposal proposal,
            GoalInterpretationInput input) {
        if (reference == null || reference.sectionId() == null) return;
        UserGoalProposal.ProposedGoal goal = proposal.getGoals().getFirst();
        UserGoalProposal.Facet referencedFacet = input.recentSectionFacet(
                reference.goalId(), reference.sectionId());
        if (!(goal.getParameters() instanceof UserGoalProposal.PortfolioFactParameters fact)
                || referencedFacet == null
                || !fact.getFacets().equals(Set.of(referencedFacet))) {
            throw new IllegalArgumentException(
                    "root.recentReference section does not match requested facet");
        }
    }

    private void requireNull(JsonNode node, String path) {
        if (node != null && !node.isNull()) {
            throw new IllegalArgumentException(path + " must be null");
        }
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
                node.get("parameters"), goalKind, input, path + ".parameters");
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
            if (kind == GoalSubjectReference.Kind.RESULT
                    || basis != GoalSubjectReference.Basis.EXPLICIT_INPUT
                    || anchor == null
                    || !input.containsPublicSubject(kind, reference)) {
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
            GoalInterpretationInput interpretationInput,
            String path) {
        requireObject(node, path);
        String parameterKind = requireText(node, "kind", 64);
        if (!parameterKind.equals(goalKind.name())) {
            throw new IllegalArgumentException(path + ".kind must match goalKind");
        }
        return switch (goalKind) {
            case PORTFOLIO_FACT -> {
                assertFields(node, Set.of("kind", "facets", "depth"),
                        Set.of("kind", "facets", "depth"), path);
                yield new UserGoalProposal.PortfolioFactParameters(decodeEnumSet(
                        requireArray(node, "facets"), UserGoalProposal.Facet.class,
                        path + ".facets", false),
                        enumValue(UserGoalProposal.Depth.class,
                                requireText(node, "depth", 64), path + ".depth"));
            }
            case PORTFOLIO_COMPARE -> {
                assertFields(node, Set.of("kind", "dimensions"),
                        Set.of("kind", "dimensions"), path);
                yield new UserGoalProposal.PortfolioCompareParameters(
                        decodeEnumSet(requireArray(node, "dimensions"),
                                UserGoalProposal.PortfolioComparisonDimension.class,
                                path + ".dimensions", false));
            }
            case PORTFOLIO_RECOMMEND -> {
                assertFields(node, Set.of("kind", "requestedSize", "constraints"),
                        Set.of("kind", "requestedSize", "constraints"), path);
                Set<String> constraints = decodeClosedNames(
                        requireArray(node, "constraints"), path + ".constraints", true);
                interpretationInput.requireAllowedRecommendationConstraints(constraints);
                yield new UserGoalProposal.PortfolioRecommendationParameters(
                        requireInt(node, "requestedSize"), constraints);
            }
            case GENERAL_EXPLANATION -> {
                assertFields(node, Set.of("kind", "topicAnchor", "depth"),
                        Set.of("kind", "topicAnchor", "depth"), path);
                yield new UserGoalProposal.GeneralExplanationParameters(
                        decodeAnchor(node.get("topicAnchor"), interpretationInput.getUserText(),
                                path + ".topicAnchor"),
                        enumValue(UserGoalProposal.Depth.class,
                                requireText(node, "depth", 64), path + ".depth"));
            }
            case GENERAL_COMPARISON -> {
                assertFields(node, Set.of("kind", "subjectAnchors", "dimensions"),
                        Set.of("kind", "subjectAnchors", "dimensions"), path);
                JsonNode anchors = requireArray(node, "subjectAnchors");
                List<UserGoalProposal.InputAnchor> decoded = new ArrayList<>();
                for (int index = 0; index < anchors.size(); index++) {
                    decoded.add(decodeAnchor(anchors.get(index), interpretationInput.getUserText(),
                            path + ".subjectAnchors[" + index + "]"));
                }
                yield new UserGoalProposal.GeneralComparisonParameters(
                        List.copyOf(decoded), decodeClosedNames(
                                requireArray(node, "dimensions"), path + ".dimensions", false));
            }
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> {
                assertFields(node, Set.of("kind", "conceptAnchor", "portfolioFacet", "depth"),
                        Set.of("kind", "conceptAnchor", "portfolioFacet", "depth"), path);
                yield new UserGoalProposal.ApplyConceptParameters(
                        decodeAnchor(node.get("conceptAnchor"), interpretationInput.getUserText(),
                                path + ".conceptAnchor"),
                        enumValue(UserGoalProposal.Facet.class,
                                requireText(node, "portfolioFacet", 64), path + ".portfolioFacet"),
                        enumValue(UserGoalProposal.Depth.class,
                                requireText(node, "depth", 64), path + ".depth"));
            }
        };
    }

    private ClarificationProposal decodeClarificationValue(
            JsonNode node, GoalInterpretationInput input) {
        requireObject(node, "clarification");
        assertFields(node, Set.of("field", "prompt", "blockedGoal"),
                Set.of("field", "prompt", "blockedGoal"), "clarification");
        ClarificationProposal.Field field = enumValue(
                ClarificationProposal.Field.class,
                requireText(node, "field", 64), "clarification.field");
        if (field == ClarificationProposal.Field.GOAL) {
            throw new IllegalArgumentException("raw goal clarification is not persistable");
        }
        BlockedGoalTemplate blockedGoal = decodeBlockedGoal(
                node.get("blockedGoal"), input, field);
        return new ClarificationProposal(
                field,
                requireText(node, "prompt", 400),
                blockedGoal);
    }

    private BlockedGoalTemplate decodeBlockedGoal(
            JsonNode node,
            GoalInterpretationInput input,
            ClarificationProposal.Field field) {
        requireObject(node, "clarification.blockedGoal");
        Set<String> fields = Set.of(
                "goalKind", "subjects", "requestedOutputs", "facets", "dimensions",
                "requestedSize", "constraints", "portfolioDepth",
                "unresolvedField", "askedFields",
                "remainingFields", "depth");
        assertFields(node, fields, fields, "clarification.blockedGoal");
        GoalKind goalKind = enumValue(GoalKind.class,
                requireText(node, "goalKind", 64), "clarification.blockedGoal.goalKind");
        if (!input.getAllowedGoalKinds().contains(goalKind)) {
            throw new IllegalArgumentException("clarification.blockedGoal.goalKind is not allowed");
        }
        ClarificationProposal.Field unresolved = enumValue(
                ClarificationProposal.Field.class,
                requireText(node, "unresolvedField", 64),
                "clarification.blockedGoal.unresolvedField");
        if (unresolved != field) {
            throw new IllegalArgumentException("clarification field must match blocked goal");
        }
        JsonNode subjectNodes = requireArray(node, "subjects");
        if (subjectNodes.size() > 5) {
            throw new IllegalArgumentException("clarification.blockedGoal.subjects is too large");
        }
        List<BlockedGoalTemplate.Subject> subjects = new ArrayList<>();
        Set<String> subjectIds = new HashSet<>();
        for (int index = 0; index < subjectNodes.size(); index++) {
            JsonNode subjectNode = subjectNodes.get(index);
            String path = "clarification.blockedGoal.subjects[" + index + "]";
            requireObject(subjectNode, path);
            assertFields(subjectNode, Set.of("kind", "reference"),
                    Set.of("kind", "reference"), path);
            GoalSubjectReference.Kind kind = enumValue(
                    GoalSubjectReference.Kind.class,
                    requireText(subjectNode, "kind", 64), path + ".kind");
            String reference = requireText(subjectNode, "reference", 128);
            if (kind == GoalSubjectReference.Kind.RESULT
                    || !input.containsPublicSubject(kind, reference)) {
                throw new IllegalArgumentException(path + " references a non-public subject");
            }
            if (!subjectIds.add(kind.name() + ':' + reference)) {
                throw new IllegalArgumentException(path + " is duplicated");
            }
            subjects.add(new BlockedGoalTemplate.Subject(kind, reference));
        }
        Set<GoalRequestedOutput> outputs = decodeEnumSet(
                requireArray(node, "requestedOutputs"), GoalRequestedOutput.class,
                "clarification.blockedGoal.requestedOutputs", true);
        Set<UserGoalProposal.Facet> facets = decodeEnumSet(
                requireArray(node, "facets"), UserGoalProposal.Facet.class,
                "clarification.blockedGoal.facets", true);
        Set<String> dimensions = decodeClosedNames(
                requireArray(node, "dimensions"),
                "clarification.blockedGoal.dimensions", true);
        Set<String> constraints = decodeClosedNames(
                requireArray(node, "constraints"),
                "clarification.blockedGoal.constraints", true);
        input.requireAllowedRecommendationConstraints(constraints);
        Set<ClarificationProposal.Field> askedFields = decodeEnumSet(
                requireArray(node, "askedFields"), ClarificationProposal.Field.class,
                "clarification.blockedGoal.askedFields", false);
        List<ClarificationProposal.Field> remainingFields = new ArrayList<>(decodeEnumSet(
                requireArray(node, "remainingFields"), ClarificationProposal.Field.class,
                "clarification.blockedGoal.remainingFields", true));
        int depth = requireInt(node, "depth");
        if (depth != 1) {
            throw new IllegalArgumentException("provider clarification depth must start at one");
        }
        return new BlockedGoalTemplate(
                goalKind, subjects, outputs, facets, dimensions,
                nullableInt(node, "requestedSize"), constraints,
                enumValue(UserGoalProposal.Depth.class,
                        requireText(node, "portfolioDepth", 64),
                        "clarification.blockedGoal.portfolioDepth"),
                unresolved, askedFields, List.copyOf(remainingFields), depth);
    }

    private GoalInterpretationResult decodeConversational(
            JsonNode root, GoalInterpretationInput input) {
        assertFields(root, Set.of("kind", "message"), Set.of("kind", "message"), "root");
        return GoalInterpretationResult.conversational(
                conversationalValidator.validate(
                        requireText(root, "message",
                                ConversationalMessageValidator.MAX_CHARACTERS),
                        input.getUserText()));
    }

    private UserGoalProposal.InputAnchor decodeAnchor(
            JsonNode node, String input, String path) {
        requireObject(node, path);
        assertFields(node, Set.of("text", "start"), Set.of("text", "start"), path);
        String text = requireText(node, "text", 256);
        int claimedStart = requireInt(node, "start");
        UserGoalProposal.InputAnchor claimed =
                new UserGoalProposal.InputAnchor(text, claimedStart);
        try {
            claimed.requireMatches(input);
            return claimed;
        } catch (IllegalArgumentException mismatch) {
            int uniqueStart = input.indexOf(text);
            if (uniqueStart < 0 || input.indexOf(text, uniqueStart + 1) >= 0) {
                throw mismatch;
            }
            return new UserGoalProposal.InputAnchor(text, uniqueStart);
        }
    }

    private void requireDiscussion(GoalInterpretationInput input) {
        if (input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.DISCUSSION) {
            throw new IllegalArgumentException(
                    "discussion route requires DISCUSSION mode");
        }
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

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        if (value.isNull()) return null;
        if (!value.isIntegralNumber()) throw new IllegalArgumentException(field + " must be an integer or null");
        return value.intValue();
    }
}
