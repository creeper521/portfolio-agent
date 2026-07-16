package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class EvidenceRecord {

    private final String id;
    private final String title;
    private final EvidenceType type;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int sourceCount;
    private final String summary;
    private final List<String> supportedClaims;
    private final EvidenceStatus publicStatus;
    private final Boolean rawContentPublic;

    @JsonCreator
    public EvidenceRecord(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("type") EvidenceType type,
            @JsonProperty("periodStart") LocalDate periodStart,
            @JsonProperty("periodEnd") LocalDate periodEnd,
            @JsonProperty("sourceCount") int sourceCount,
            @JsonProperty("summary") String summary,
            @JsonProperty("supportedClaims") List<String> supportedClaims,
            @JsonProperty("publicStatus") EvidenceStatus publicStatus,
            @JsonProperty("rawContentPublic") Boolean rawContentPublic
    ) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.sourceCount = sourceCount;
        this.summary = summary;
        this.supportedClaims = List.copyOf(supportedClaims);
        this.publicStatus = publicStatus;
        this.rawContentPublic = rawContentPublic;
    }

    public String getId() {
        return id;
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

    public List<String> getSupportedClaims() {
        return supportedClaims;
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
                && Objects.equals(title, that.title)
                && Objects.equals(type, that.type)
                && Objects.equals(periodStart, that.periodStart)
                && Objects.equals(periodEnd, that.periodEnd)
                && Objects.equals(summary, that.summary)
                && Objects.equals(supportedClaims, that.supportedClaims)
                && Objects.equals(publicStatus, that.publicStatus)
                && Objects.equals(rawContentPublic, that.rawContentPublic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, type, periodStart, periodEnd, sourceCount, summary,
                supportedClaims, publicStatus, rawContentPublic);
    }

    @Override
    public String toString() {
        return "EvidenceRecord{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", sourceCount=" + sourceCount +
                ", summary='" + summary + '\'' +
                ", supportedClaims=" + supportedClaims +
                ", publicStatus=" + publicStatus +
                ", rawContentPublic=" + rawContentPublic +
                '}';
    }
}
