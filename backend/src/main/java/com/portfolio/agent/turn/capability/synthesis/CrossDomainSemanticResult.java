package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.capability.general.GeneralSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.execution.TaskSemanticResult;

import java.util.List;
import java.util.Objects;

/**
 * 跨域综合能力的语义结果，不可变 {@link TaskSemanticResult}：以计划锚定的概念
 * 为轴，汇聚一侧的通用知识陈述（来自 GENERAL 域）与另一侧的落地项目陈述
 * （来自 PORTFOLIO 域），两侧输入都非空才有效。构造时全部校验并冻结为不可变列表。
 */
public final class CrossDomainSemanticResult implements TaskSemanticResult {
    private final String conceptAnchor;
    private final List<GeneralSemanticResult.Statement> generalStatements;
    private final List<GroundedPortfolioStatement> portfolioStatements;
    private final List<String> caveats;

    public CrossDomainSemanticResult(
            String conceptAnchor,
            List<GeneralSemanticResult.Statement> generalStatements,
            List<GroundedPortfolioStatement> portfolioStatements,
            List<String> caveats) {
        if (conceptAnchor == null || conceptAnchor.isBlank()) {
            throw new IllegalArgumentException("conceptAnchor is required");
        }
        this.conceptAnchor = conceptAnchor.trim();
        this.generalStatements = List.copyOf(Objects.requireNonNull(generalStatements, "generalStatements"));
        this.portfolioStatements = List.copyOf(Objects.requireNonNull(portfolioStatements, "portfolioStatements"));
        this.caveats = List.copyOf(Objects.requireNonNull(caveats, "caveats"));
        if (this.generalStatements.isEmpty() || this.portfolioStatements.isEmpty()) {
            throw new IllegalArgumentException("both semantic inputs are required");
        }
    }

    public String getConceptAnchor() { return conceptAnchor; }
    public List<GeneralSemanticResult.Statement> getGeneralStatements() { return generalStatements; }
    public List<GroundedPortfolioStatement> getPortfolioStatements() { return portfolioStatements; }
    public List<String> getCaveats() { return caveats; }

    /** 有据可依的项目陈述：主体 ID + 主张类别 + 陈述文本 + 必填的公开来源引用。 */
    public record GroundedPortfolioStatement(
            String subjectId, AnswerClaimCategory category, String text,
            PublicSourceReferenceValue sourceReference) {
        public GroundedPortfolioStatement {
            if (subjectId == null || subjectId.isBlank() || text == null || text.isBlank()) {
                throw new IllegalArgumentException("grounded statement is invalid");
            }
            subjectId = subjectId.trim();
            text = text.trim();
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(sourceReference, "sourceReference");
        }
    }
}
