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

    public String summaryPrompt(Object conversation) {
        return prompt("summary", conversation, null);
    }

    public String toolPlanPrompt(Object conversation, Object approvedContext) {
        return prompt("tool_plan", conversation, approvedContext);
    }

    public String generationPrompt(Object conversation, Object approvedContext) {
        return prompt("generation", conversation, approvedContext);
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
            case "intent" -> """
                    只输出一个 JSON 对象，字段必须且只能是：
                    intent: CONVERSATION|GENERAL_KNOWLEDGE|PORTFOLIO_GROUNDED|HYBRID|TIME_SENSITIVE|UNSUPPORTED_OR_UNSAFE
                    answerScope: CONVERSATION|GENERAL|PORTFOLIO|HYBRID
                    confidence: 0 到 1 的数字
                    projectSlug: 字符串或 null
                    caseSlug: 字符串或 null
                    facet: OVERVIEW|IMPLEMENTATION|DECISION|CHALLENGE|INCIDENT|VERIFICATION|LIMITATION|LEARNING
                    clarificationRequired: boolean
                    示例：{"intent":"GENERAL_KNOWLEDGE","answerScope":"GENERAL","confidence":0.98,"projectSlug":null,"caseSlug":null,"facet":"OVERVIEW","clarificationRequired":false}
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
