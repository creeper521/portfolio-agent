package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConversationalPromptFactory {

    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public ConversationalPromptFactory(ObjectMapper objectMapper, String systemPrompt) {
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    public String systemPrompt(String operation) {
        return systemPrompt
                + "\n\n当前任务：" + operation
                + "\n\n输出契约：\n" + outputContract(operation);
    }

    public String intentPrompt(Object conversation, Object publicSubjects) {
        return prompt("intent", conversation, publicSubjects);
    }

    public String semanticRoutingPrompt(Object routingInput) {
        return prompt("semantic_route", routingInput, null);
    }

    public String summaryPrompt(Object conversation) {
        return prompt("summary", conversation, null);
    }

    public String generationPrompt(Object conversation, Object approvedContext) {
        return prompt("generation", conversation, approvedContext);
    }

    public String generalMaterialPrompt(Object conversation, Object approvedContext) {
        return prompt("general_answer_material", conversation, approvedContext);
    }

    public String crossDomainExpressionPrompt(Object approvedMaterial) {
        return prompt("cross_domain_expression", null, approvedMaterial);
    }

    public String reviewPrompt(Object blocks, Object approvedContext) {
        return prompt("review", blocks, approvedContext);
    }

    public String suggestionPrompt(Object conversation, Object approvedContext) {
        return prompt("suggestion", conversation, approvedContext);
    }

    private String prompt(String operation, Object untrusted, Object approved) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("operation", operation);
        envelope.put("payload", untrusted);
        StringBuilder prompt = new StringBuilder();
        prompt.append("<untrusted_conversation>\n")
                .append(json(envelope))
                .append("\n</untrusted_conversation>");
        if (approved != null) {
            prompt.append("\n<approved_portfolio_context>\n")
                    .append(json(approved))
                    .append("\n</approved_portfolio_context>");
        }
        return prompt.toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("unable to serialize prompt payload", exception);
        }
    }

    private String outputContract(String operation) {
        return switch (operation) {
            case "semantic_route" -> """
                    Return exactly one JSON object with these fields only:
                    taskCandidates, dependencyCandidates, exclusionCandidates.
                    All three fields must be arrays. Do not add undeclared fields.
                    Each task candidate must contain exactly taskType, questionSpan, subjects,
                    dimensions and requestedOutputs. questionSpan must be copied verbatim from the current question.
                    taskType must be one of PORTFOLIO_FACT, PORTFOLIO_COMPARE, PORTFOLIO_RECOMMEND,
                    PORTFOLIO_REFINE_RECOMMENDATION, GENERAL_EXPLANATION, GENERAL_COMPARISON or SYNTHESIS.
                    subjects may contain only subjectType and subjectId values present in publicSubjects.
                    dimensions and requestedOutputs must use declared enum names.
                    Each dependency candidate must contain exactly fromTaskIndex, toTaskIndex and dependencyType.
                    Each exclusion candidate must contain exactly scope, exclusionType, taskIndex and exactly one
                    controlled value matching its exclusionType.
                    Never invent identifiers, free-text goals, hidden state, tools or execution instructions.
                    """;
            case "intent" -> """
                    只输出一个 JSON 对象，字段必须且只能是：
                    intent: CONVERSATION|GENERAL_KNOWLEDGE|PORTFOLIO_GROUNDED|HYBRID|TIME_SENSITIVE|UNSUPPORTED_OR_UNSAFE
                    answerScope: CONVERSATION|GENERAL|PORTFOLIO|HYBRID
                    confidence: 0 到 1 的数字
                    projectSlug: 字符串或 null
                    caseSlug: 字符串或 null
                    facet: OVERVIEW|RESPONSIBILITY|IMPLEMENTATION|DECISION|CHALLENGE|INCIDENT|VERIFICATION|LIMITATION|LEARNING|OUTCOME
                    clarificationRequired: boolean
                    示例：{"intent":"GENERAL_KNOWLEDGE","answerScope":"GENERAL","confidence":0.98,"projectSlug":null,"caseSlug":null,"facet":"OVERVIEW","clarificationRequired":false}
                    不要输出未声明字段。
                    """;
            case "portfolio_task" -> """
                    只输出一个 JSON 对象，字段必须且只能是：boundaryIntent、mode、conditions、refinement、confidence。
                    boundaryIntent 只能是 TIME_SENSITIVE|UNSUPPORTED_OR_UNSAFE|null。
                    boundaryIntent 非 null 时，mode 必须为 null；boundaryIntent 为 null 时，mode 必须非 null。
                    mode 只能是 FACT_LOOKUP|COMPARISON|RECOMMENDATION|REFINE_RECOMMENDATION|CLARIFICATION_REQUIRED。
                    conditions 的字段必须且只能是 careerTrack、audienceRole、capabilityCodes、goal、requestedSize。
                    careerTrack、audienceRole、goal 没有值时使用 null；capabilityCodes 必须是字符串数组；requestedSize 必须是 2 到 5 的整数或 null。
                    refinement 只能在 mode 为 REFINE_RECOMMENDATION 时提供；其字段必须且只能是 conditions、excludedPortfolioIds；否则使用 null。
                    confidence 必须是 0 到 1 的数字。不要输出 SQL、检索器、策略或作品 ID。
                    不要输出未声明字段。
                    """;
            case "tool_plan" -> """
                    只输出一个 JSON 对象：{"calls":[...]}。
                    每个 call 的字段必须且只能是 kind、projectSlugs、caseSlugs、claimIds、sectionType。
                    kind 只能是 GET_PROJECT|GET_CASE|GET_CLAIMS|GET_EVIDENCE_FOR_CLAIMS|GET_TIMELINE|SEARCH_PUBLIC_CONTENT|COMPARE_PROJECTS。
                    projectSlugs、caseSlugs、claimIds 必须是字符串数组；没有值时使用 []。
                    sectionType 只能是 BACKGROUND|RESPONSIBILITY|SOLUTION|VERIFICATION|STATUS|BOUNDARY|REJECTED 或 null。
                    不需要工具时输出 {"calls":[]}。不要输出未声明字段。
                    """;
            case "generation" -> """
                    只输出一个 JSON 对象，字段必须且只能是 title、resolution、blocks。
                    resolution 只能是 ANSWERED|BOUNDARY|REJECTED。
                    blocks 必须是数组；每个 block 的字段必须且只能是 sourceScope、content、claimIds、evidenceIds。
                    sourceScope 只能是 GENERAL|PORTFOLIO。
                    claimIds、evidenceIds 必须是字符串数组；通用内容必须使用 []；作品集内容只能使用 approved_portfolio_context 中存在的 ID。
                    示例：{"title":"REST API","resolution":"ANSWERED","blocks":[{"sourceScope":"GENERAL","content":"REST API 是一种接口设计风格。","claimIds":[],"evidenceIds":[]}]}
                    不要把推荐问题放入本对象。不要输出未声明字段。
                    """;
            case "general_answer_material" -> """
                    只输出一个 JSON 对象，字段必须且只能是 schemaVersion、topic、statements、caveats、metadata。
                    schemaVersion 必须是 general-material-v1。statements 至少一项；每项只能包含 statementAlias、text、role、conceptTags、supportKind、publicSourceKeys。
                    role 必须是 DEFINITION|MECHANISM|ADVANTAGE|LIMITATION|USE_CASE|CONTRAST|PRACTICE|CAUTION 之一。
                    supportKind 必须是 GENERAL_KNOWLEDGE，publicSourceKeys 必须始终为空数组；不得输出 Portfolio 事实、来源 ID 或自由对话摘要。
                    caveats 每项只能包含 alias、text；metadata 只能包含 contentVersion、audienceRole、discourseAliases。
                    metadata.contentVersion 与 metadata.audienceRole 必须原样复制 approved_portfolio_context 中的同名字段，discourseAliases 必须是字符串数组。
                    不要输出未声明字段。
                    """;
            case "cross_domain_expression" -> """
                    只输出一个 JSON 对象，字段必须且只能是 schemaVersion、sectionKind、text、relationAlias、statementAliases、caveatAliases。
                    schemaVersion 必须是 cross-domain-expression-v1；relationAlias 必须原样使用已批准关系；
                    不得新增关系、修改 Portfolio 原子事实、制造来源或删除输入 Caveat。
                    不要输出未声明字段。
                    """;
            case "review" -> """
                    只输出一个 JSON 对象，字段必须且只能是 unsupportedBlockIndexes、reasonCodes。
                    两个字段都必须是数组；没有问题时输出 {"unsupportedBlockIndexes":[],"reasonCodes":[]}。
                    unsupportedBlockIndexes 只能包含输入 blocks 的零基索引。不要输出未声明字段。
                    """;
            case "suggestion" -> """
                    只输出一个 JSON 对象：{"questions":[...]}。
                    questions 必须包含 0 到 3 项；每项字段必须且只能是 text、projectSlug、caseSlug、facet。
                    projectSlug、caseSlug、facet 没有值时使用 null；不得编造 approved_portfolio_context 中不存在的 slug。
                    不要输出顶层数组。不要输出未声明字段。
                    """;
            case "summary" -> """
                    只输出一个 JSON 对象，字段必须且只能是 summary。
                    summary 必须是简洁字符串，只总结对话，不新增作者事实。
                    示例：{"summary":"访客询问了 REST API 的基本概念。"}
                    不要输出未声明字段。
                    """;
            default -> throw new IllegalArgumentException(
                    "unsupported conversation operation: " + operation);
        };
    }
}
