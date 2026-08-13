package com.portfolio.agent.answer.composition.domain;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class FactAnswerMaterial extends PortfolioAnswerMaterial {
    private final SubjectReference subject;
    private final FocusMode focusMode;
    private final List<FactSection> sections;
    private final SummaryPolicy summaryPolicy;

    public FactAnswerMaterial(String publicTitle, SubjectReference subject, FocusMode focusMode,
            List<FactSection> sections, SummaryPolicy summaryPolicy, List<String> fixedCaveats,
            List<String> omittedTopicLabels) {
        super(publicTitle, fixedCaveats, omittedTopicLabels);
        this.subject = Objects.requireNonNull(subject, "subject");
        this.focusMode = Objects.requireNonNull(focusMode, "focusMode");
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        this.summaryPolicy = Objects.requireNonNull(summaryPolicy, "summaryPolicy");
        SummaryPolicy expected = focusMode == FocusMode.OVERVIEW
                ? SummaryPolicy.REQUIRED : SummaryPolicy.FORBIDDEN;
        if (summaryPolicy != expected) {
            throw new IllegalArgumentException("summaryPolicy does not match focusMode");
        }
        LinkedHashSet<AnswerSectionType> sectionTypes = new LinkedHashSet<>();
        for (FactSection section : this.sections) {
            if (!sectionTypes.add(section.getSectionType())) {
                throw new IllegalArgumentException("duplicate fact section type");
            }
        }
    }

    @Override public MaterialKind getMaterialKind() { return MaterialKind.FACT; }
    public SubjectReference getSubject() { return subject; }
    public FocusMode getFocusMode() { return focusMode; }
    public List<FactSection> getSections() { return sections; }
    public SummaryPolicy getSummaryPolicy() { return summaryPolicy; }
    @Override public List<ExpressionStatement> getExpressionStatements() {
        return sections.stream().flatMap(section -> section.getStatementEntries().stream()).toList();
    }
    @Override public List<String> getPublicSubjectLabels() { return List.of(subject.getPublicLabel()); }

    public static final class FactSection {
        private final AnswerSectionType sectionType;
        private final List<ExpressionStatement> statementEntries;
        private final OrderingPolicy orderingPolicy;

        public FactSection(AnswerSectionType sectionType, List<ExpressionStatement> statementEntries,
                OrderingPolicy orderingPolicy) {
            this.sectionType = Objects.requireNonNull(sectionType, "sectionType");
            if (sectionType == AnswerSectionType.BOUNDARY || sectionType == AnswerSectionType.REJECTED) {
                throw new IllegalArgumentException("fact section is server-owned");
            }
            this.statementEntries = List.copyOf(
                    Objects.requireNonNull(statementEntries, "statementEntries"));
            this.orderingPolicy = Objects.requireNonNull(orderingPolicy, "orderingPolicy");
            requireUniqueOrders(this.statementEntries, "statementEntries");
            if (this.statementEntries.stream().anyMatch(entry -> entry.getAllowedSection() != sectionType)) {
                throw new IllegalArgumentException("statement is not authorized for fact section");
            }
        }

        public AnswerSectionType getSectionType() { return sectionType; }
        public List<ExpressionStatement> getStatementEntries() { return statementEntries; }
        public OrderingPolicy getOrderingPolicy() { return orderingPolicy; }
    }
}
