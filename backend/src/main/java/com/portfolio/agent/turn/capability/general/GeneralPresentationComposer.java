package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.execution.AnswerSectionType;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用能力展示组装器：把校验通过的 {@link GeneralSemanticResult} 翻译为
 * {@link GeneralPresentation}——每条陈述映射为一个分节（DEFINITION 映射
 * BACKGROUND，MECHANISM/COMPARISON 映射 SOLUTION），caveats 非空时追加一个
 * "适用边界"分节。只做结构映射，不改写语义文本。
 */
public final class GeneralPresentationComposer {
    /** 组装展示：陈述顺序即分节顺序；主题作为展示标题。 */
    public GeneralPresentation compose(GeneralSemanticResult result) {
        List<GeneralPresentation.Section> sections = new ArrayList<>();
        for (GeneralSemanticResult.Statement statement : result.getStatements()) {
            sections.add(new GeneralPresentation.Section(
                    sectionType(statement.getRole()), title(statement), statement.getText()));
        }
        if (!result.getCaveats().isEmpty()) {
            sections.add(new GeneralPresentation.Section(
                    AnswerSectionType.BOUNDARY, "适用边界", String.join("\n", result.getCaveats())));
        }
        return new GeneralPresentation(result.getTopic(), sections);
    }

    /** 角色到节类型的映射：定义走 BACKGROUND 背景，机制/对比走 SOLUTION 正文。 */
    private AnswerSectionType sectionType(GeneralSemanticResult.Role role) {
        return role == GeneralSemanticResult.Role.DEFINITION
                ? AnswerSectionType.BACKGROUND : AnswerSectionType.SOLUTION;
    }

    /** 分节标题：定义/机制用固定中文标题，对比用 "主体 · 维度" 拼接。 */
    private String title(GeneralSemanticResult.Statement statement) {
        return switch (statement.getRole()) {
            case DEFINITION -> "概念";
            case MECHANISM -> "机制";
            case COMPARISON -> statement.getSubject() + " · " + statement.getDimension();
        };
    }
}
