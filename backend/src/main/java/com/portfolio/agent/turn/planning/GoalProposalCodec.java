package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 目标提案编解码器：把模型 JSON 输出严格解码为类型化解释结果。
 *
 * <p>采用严格 Jackson 配置（禁止重复键、禁止尾随令牌）与逐字段白名单
 * 校验；所有主体、约束、路由都必须落在 {@link GoalInterpretationInput}
 * 给定的封闭范围内，锚点必须能对上访客原文。任何越界输出都抛出
 * IllegalArgumentException，由上层按模型失败或 fail-closed 路径处理。</p>
 */
public final class GoalProposalCodec {

    /** 输出 schema 版本标识，随提示词契约演进。 */
    public static final String SCHEMA_VERSION = "goal.proposal.v5";
    private static final int MAX_OUTPUT_CHARACTERS = 20000;
    private final ObjectMapper mapper;
    private final ConversationalMessageValidator conversationalValidator;

    /** 构造编解码器：启用重复键检测与尾随令牌拒绝的严格 Jackson 配置。 */
    public GoalProposalCodec() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.conversationalValidator = new ConversationalMessageValidator();
    }

    /**
     * 解码模型输出的目标提案 JSON。
     *
     * @throws IllegalArgumentException JSON 为空或超长（超过 20000 字符）、
     *         格式非法、kind 不支持或任何字段校验失败
     */
    public GoalInterpretationResult decode(String json, GoalInterpretationInput input) {
        if (json == null || json.isBlank() || json.length() > MAX_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException("goal proposal output must be non-empty and bounded");
        }
        JsonNode root = readStrict(json);
        return decode(root, input);
    }

    /**
     * 从已经过严格 parser 与 canonical schema 校验的同一棵 JSON tree 构造领域结果。
     * 该入口不再次序列化或解析 Provider 输出。
     */
    public GoalInterpretationResult decode(JsonNode root, GoalInterpretationInput input) {
        requireObject(root, "root");
        String kind = requireText(root, "kind", 64);
        return switch (kind) {
            case "SEMANTIC_ROUTE" -> decodeSemanticRoute(root, input);
            case "CONVERSATIONAL" -> decodeConversational(root, input);
            default -> throw new GoalProposalDecodeException(
                    GoalProposalDecodeException.Reason.UNSUPPORTED_ROOT_KIND,
                    "unsupported goal interpretation kind");
        };
    }

    /** 按路由分支解码语义路由提案：每条路由只允许各自的字段组合，越界字段直接拒绝。 */
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

    /** 解码最近语义引用，并校验其确实存在于 typed 最近语义状态中。 */
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

    /** 校验引用的小节与请求的 PORTFOLIO_FACT 侧面一致，防止跨小节续接。 */
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

    /** 断言 JSON 节点不存在或为 null。 */
    private void requireNull(JsonNode node, String path) {
        if (node != null && !node.isNull()) {
            throw new IllegalArgumentException(path + " must be null");
        }
    }

    /** 解码单个提案目标：字段白名单 + 目标类别/主体/参数的封闭校验。 */
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

    /**
     * 解码主体候选列表。
     *
     * <p>仅接受 EXPLICIT_INPUT 依据、带锚点且在公开目录内的非 RESULT 主体，
     * 并拒绝重复主体。</p>
     */
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

    /** 按 goalKind 分支解码类型化目标参数；参数 kind 必须与 goalKind 一致。 */
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

    /** 解码澄清值：字段不允许为 GOAL（原始目标级澄清不可持久化）。 */
    private ClarificationProposal decodeClarificationValue(
            JsonNode node, GoalInterpretationInput input) {
        requireObject(node, "clarification");
        JsonNode blockedGoalNode = node.get("blockedGoal");
        requireBlockedGoalObject(blockedGoalNode);
        assertFields(node, Set.of("field", "prompt", "blockedGoal"),
                Set.of("field", "prompt", "blockedGoal"), "clarification");
        ClarificationProposal.Field field = enumValue(
                ClarificationProposal.Field.class,
                requireText(node, "field", 64), "clarification.field");
        if (field == ClarificationProposal.Field.GOAL) {
            throw new IllegalArgumentException("raw goal clarification is not persistable");
        }
        BlockedGoalTemplate blockedGoal = decodeBlockedGoal(
                blockedGoalNode, input, field);
        return new ClarificationProposal(
                field,
                requireText(node, "prompt", 400),
                blockedGoal);
    }

    /** 解码被阻塞目标模板并逐字段做封闭校验；Provider 侧澄清深度必须为 1。 */
    private BlockedGoalTemplate decodeBlockedGoal(
            JsonNode node,
            GoalInterpretationInput input,
            ClarificationProposal.Field field) {
        requireBlockedGoalObject(node);
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

    /** 标准澄清只能携带完整对象；缺失、null 与非对象共享一个闭集原因。 */
    private void requireBlockedGoalObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new GoalProposalDecodeException(
                    GoalProposalDecodeException.Reason
                            .CLARIFICATION_BLOCKED_GOAL_REQUIRED,
                    "clarification.blockedGoal must be an object");
        }
    }

    /** 解码纯对话结果，文案必须通过 {@link ConversationalMessageValidator}。 */
    private GoalInterpretationResult decodeConversational(
            JsonNode root, GoalInterpretationInput input) {
        assertFields(root, Set.of("kind", "message"), Set.of("kind", "message"), "root");
        return GoalInterpretationResult.conversational(
                conversationalValidator.validate(
                        requireText(root, "message",
                                ConversationalMessageValidator.MAX_CHARACTERS),
                        input.getUserText()));
    }

    /** 解码输入锚点；文本或位置不精确匹配时直接拒绝，不修复 Provider 输出。 */
    private UserGoalProposal.InputAnchor decodeAnchor(
            JsonNode node, String input, String path) {
        requireObject(node, path);
        assertFields(node, Set.of("text", "start"), Set.of("text", "start"), path);
        String text = requireText(node, "text", 256);
        int claimedStart = requireInt(node, "start");
        UserGoalProposal.InputAnchor claimed =
                new UserGoalProposal.InputAnchor(text, claimedStart);
        claimed.requireMatches(input);
        return claimed;
    }

    /** 断言解释输入处于 DISCUSSION 模式（讨论类路由的前置条件）。 */
    private void requireDiscussion(GoalInterpretationInput input) {
        if (input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.DISCUSSION) {
            throw new IllegalArgumentException(
                    "discussion route requires DISCUSSION mode");
        }
    }

    /**
     * 解码闭合命名集合：仅接受符合 {@code [A-Z_]{1,64}} 的非重复名称。
     *
     * <p>与 {@link #decodeEnumSet} 不同，这里只约束命名形态，具体取值是否
     * 在允许范围内由调用方（如推荐约束白名单）进一步校验。</p>
     */
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

    /** 解码枚举集合为不可变 Set，拒绝重复值；allowEmpty 为 false 时空集合也拒绝。 */
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

    /** 按名称解析枚举值，缺失或不支持的名称统一转为 IllegalArgumentException。 */
    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
        if (value == null) throw new IllegalArgumentException(path + " is required");
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(path + " is not supported", failure);
        }
    }

    /** 用严格 mapper 读取 JSON 树；解析失败统一转为 IllegalArgumentException。 */
    private JsonNode readStrict(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("invalid goal proposal JSON", failure);
        }
    }

    /** 字段白名单闸门：出现未知字段或缺失必填字段即拒绝整个节点。 */
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

    /** 断言 JSON 节点存在且为对象。 */
    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
    }

    /** 取出指定字段并断言其为数组。 */
    private JsonNode requireArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    /** 取出指定字段并断言其为非空、长度不超过 maximum 的文本。 */
    private String requireText(JsonNode node, String field, int maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()
                || value.asText().isBlank() || value.asText().length() > maximum) {
            throw new IllegalArgumentException(field + " must be non-empty and bounded");
        }
        return value.asText();
    }

    /** 取出指定字段并断言其为整数。 */
    private int requireInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.intValue();
    }

    /** 取出可空的整数字段：字段必须存在，值可为 JSON null 或整数。 */
    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        if (value.isNull()) return null;
        if (!value.isIntegralNumber()) throw new IllegalArgumentException(field + " must be an integer or null");
        return value.intValue();
    }
}
