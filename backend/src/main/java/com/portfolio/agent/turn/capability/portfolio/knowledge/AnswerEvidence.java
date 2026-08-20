package com.portfolio.agent.turn.capability.portfolio.knowledge;

import java.time.LocalDate;
import java.util.Objects;

public final class AnswerEvidence {

    private final String id;
    private final String code;
    private final String title;
    private final String type;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int sourceCount;
    private final String summary;
    private final String publicStatus;
    private final boolean rawContentPublic;

    public AnswerEvidence(
            String id,
            String code,
            String title,
            String type,
            LocalDate periodStart,
            LocalDate periodEnd,
            int sourceCount,
            String summary,
            String publicStatus,
            boolean rawContentPublic
    ) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.type = type;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.sourceCount = sourceCount;
        this.summary = summary;
        this.publicStatus = publicStatus;
        this.rawContentPublic = rawContentPublic;
    }

    public AnswerEvidence(
            String id,
            String title,
            String type,
            LocalDate periodStart,
            LocalDate periodEnd,
            int sourceCount,
            String summary,
            String publicStatus,
            boolean rawContentPublic
    ) {
        this(id, id, title, type, periodStart, periodEnd, sourceCount, summary,
                publicStatus, rawContentPublic);
    }

    public String getId() {
        return id;
    }

    public String getCode() { return code; }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public String getSummary() {
        return summary;
    }

    public String getPublicStatus() {
        return publicStatus;
    }

    public boolean isRawContentPublic() {
        return rawContentPublic;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerEvidence that)) {
            return false;
        }
        return sourceCount == that.sourceCount
                && rawContentPublic == that.rawContentPublic
                && Objects.equals(id, that.id)
                && Objects.equals(code, that.code)
                && Objects.equals(title, that.title)
                && Objects.equals(type, that.type)
                && Objects.equals(periodStart, that.periodStart)
                && Objects.equals(periodEnd, that.periodEnd)
                && Objects.equals(summary, that.summary)
                && Objects.equals(publicStatus, that.publicStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code, title, type, periodStart, periodEnd, sourceCount, summary,
                publicStatus, rawContentPublic);
    }

    @Override
    public String toString() {
        return "AnswerEvidence{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", sourceCount=" + sourceCount +
                ", summary='" + summary + '\'' +
                ", publicStatus='" + publicStatus + '\'' +
                ", rawContentPublic=" + rawContentPublic +
                '}';
    }
}
