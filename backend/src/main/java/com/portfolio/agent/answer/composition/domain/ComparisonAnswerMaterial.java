package com.portfolio.agent.answer.composition.domain;

import java.util.List;
import java.util.Objects;

public final class ComparisonAnswerMaterial extends PortfolioAnswerMaterial {
    private final List<SubjectReference> orderedSubjects;
    private final List<ComparisonDimension> orderedDimensions;

    public ComparisonAnswerMaterial(String publicTitle, List<SubjectReference> orderedSubjects,
            List<ComparisonDimension> orderedDimensions, List<String> fixedCaveats,
            List<String> omittedTopicLabels) {
        super(publicTitle, fixedCaveats, omittedTopicLabels);
        this.orderedSubjects = DomainValues.distinctCopy(orderedSubjects, "orderedSubjects");
        if (this.orderedSubjects.size() < 2) {
            throw new IllegalArgumentException("comparison requires at least two subjects");
        }
        this.orderedDimensions = List.copyOf(
                Objects.requireNonNull(orderedDimensions, "orderedDimensions"));
        DomainValues.distinctTextCopy(this.orderedDimensions.stream()
                .map(ComparisonDimension::getDimensionKey).toList(), "dimensionKeys");
        for (ComparisonDimension dimension : this.orderedDimensions) {
            if (!dimension.getSubjectCells().stream().map(SubjectCell::getSubjectReference).toList()
                    .equals(this.orderedSubjects)) {
                throw new IllegalArgumentException("comparison cell subject order mismatch");
            }
        }
    }

    @Override public MaterialKind getMaterialKind() { return MaterialKind.COMPARISON; }
    public List<SubjectReference> getOrderedSubjects() { return orderedSubjects; }
    public List<ComparisonDimension> getOrderedDimensions() { return orderedDimensions; }
    @Override public List<ExpressionStatement> getExpressionStatements() {
        return orderedDimensions.stream().flatMap(dimension -> dimension.allEntries().stream()).toList();
    }
    @Override public List<String> getPublicSubjectLabels() {
        return orderedSubjects.stream().map(SubjectReference::getPublicLabel).toList();
    }

    public static final class ComparisonDimension {
        private final String dimensionKey;
        private final List<SubjectCell> subjectCells;
        private final ExpressionStatement controlledRelation;

        public ComparisonDimension(String dimensionKey, List<SubjectCell> subjectCells,
                ExpressionStatement controlledRelation) {
            this.dimensionKey = DomainValues.requireText(dimensionKey, "dimensionKey");
            this.subjectCells = List.copyOf(Objects.requireNonNull(subjectCells, "subjectCells"));
            this.controlledRelation = controlledRelation;
            if (controlledRelation != null
                    && controlledRelation.getStatement().getStatementType() != StatementType.CONTROLLED_RELATION) {
                throw new IllegalArgumentException("controlledRelation must be a relation statement");
            }
        }
        public String getDimensionKey() { return dimensionKey; }
        public List<SubjectCell> getSubjectCells() { return subjectCells; }
        public ExpressionStatement getControlledRelation() { return controlledRelation; }
        private List<ExpressionStatement> allEntries() {
            java.util.ArrayList<ExpressionStatement> entries = new java.util.ArrayList<>();
            subjectCells.forEach(cell -> entries.addAll(cell.getStatementEntries()));
            if (controlledRelation != null) entries.add(controlledRelation);
            return List.copyOf(entries);
        }
    }

    public static final class SubjectCell {
        private final SubjectReference subjectReference;
        private final CoverageState coverageState;
        private final List<ExpressionStatement> statementEntries;
        public SubjectCell(SubjectReference subjectReference, CoverageState coverageState,
                List<ExpressionStatement> statementEntries) {
            this.subjectReference = Objects.requireNonNull(subjectReference, "subjectReference");
            this.coverageState = Objects.requireNonNull(coverageState, "coverageState");
            this.statementEntries = List.copyOf(
                    Objects.requireNonNull(statementEntries, "statementEntries"));
            requireUniqueOrders(this.statementEntries, "statementEntries");
        }
        public SubjectReference getSubjectReference() { return subjectReference; }
        public CoverageState getCoverageState() { return coverageState; }
        public List<ExpressionStatement> getStatementEntries() { return statementEntries; }
    }
}
