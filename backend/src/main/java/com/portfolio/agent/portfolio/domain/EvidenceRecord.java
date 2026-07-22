package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.Objects;

public final class EvidenceRecord {

    private final String id;
    private final String code;
    private final String title;
    private final EvidenceType type;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int sourceCount;
    private final String summary;
    private final EvidenceStatus publicStatus;
    private final Boolean rawContentPublic;

    @JsonCreator
    public EvidenceRecord(
            @JsonProperty("id") String id,
            @JsonProperty("code") String code,
            @JsonProperty("title") String title,
            @JsonProperty("type") EvidenceType type,
            @JsonProperty("periodStart") LocalDate periodStart,
            @JsonProperty("periodEnd") LocalDate periodEnd,
            @JsonProperty("sourceCount") int sourceCount,
            @JsonProperty("summary") String summary,
            @JsonProperty("publicStatus") EvidenceStatus publicStatus,
            @JsonProperty("rawContentPublic") Boolean rawContentPublic
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

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public EvidenceType getType() {
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

    public EvidenceStatus getPublicStatus() {
        return publicStatus;
    }

    public Boolean getRawContentPublic() {
        return rawContentPublic;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EvidenceRecord that)) {
            return false;
        }
        return sourceCount == that.sourceCount
                && Objects.equals(id, that.id)
                && Objects.equals(code, that.code)
                && Objects.equals(title, that.title)
                && Objects.equals(type, that.type)
                && Objects.equals(periodStart, that.periodStart)
                && Objects.equals(periodEnd, that.periodEnd)
                && Objects.equals(summary, that.summary)
                && Objects.equals(publicStatus, that.publicStatus)
                && Objects.equals(rawContentPublic, that.rawContentPublic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code, title, type, periodStart, periodEnd, sourceCount, summary,
                publicStatus, rawContentPublic);
    }

    @Override
    public String toString() {
        return "EvidenceRecord{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", sourceCount=" + sourceCount +
                ", summary='" + summary + '\'' +
                ", publicStatus=" + publicStatus +
                ", rawContentPublic=" + rawContentPublic +
                '}';
    }
}
