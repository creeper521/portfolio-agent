package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.capability.general.GeneralSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.evidence.PublicSourceReference;
import com.portfolio.agent.turn.execution.TaskSemanticResult;

import java.util.List;
import java.util.Objects;

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

    public record GroundedPortfolioStatement(
            String subjectId, String text, PublicSourceReference sourceReference) {
        public GroundedPortfolioStatement {
            if (subjectId == null || subjectId.isBlank() || text == null || text.isBlank()) {
                throw new IllegalArgumentException("grounded statement is invalid");
            }
            subjectId = subjectId.trim();
            text = text.trim();
            Objects.requireNonNull(sourceReference, "sourceReference");
        }
    }
}
