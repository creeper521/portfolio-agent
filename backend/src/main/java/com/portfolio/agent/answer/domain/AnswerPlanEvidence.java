package com.portfolio.agent.answer.domain;

import java.time.LocalDate;

public final class AnswerPlanEvidence {

    private final String id;
    private final String title;
    private final String type;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int sourceCount;
    private final String summary;

    public AnswerPlanEvidence(
            String id,
            String title,
            String type,
            LocalDate periodStart,
            LocalDate periodEnd,
            int sourceCount,
            String summary
    ) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.sourceCount = sourceCount;
        this.summary = summary;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public int getSourceCount() { return sourceCount; }
    public String getSummary() { return summary; }
}
