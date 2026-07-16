package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.EvidenceType;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class EvidenceResponse {

    private final String id;
    private final String title;
    private final EvidenceType type;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int sourceCount;
    private final String summary;
    private final List<String> supportedClaims;
    private final EvidenceStatus publicStatus;
    private final boolean rawContentPublic;

    public EvidenceResponse(
            String id,
            String title,
            EvidenceType type,
            LocalDate periodStart,
            LocalDate periodEnd,
            int sourceCount,
            String summary,
            List<String> supportedClaims,
            EvidenceStatus publicStatus,
            boolean rawContentPublic
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

    public static EvidenceResponse from(EvidenceRecord evidence) {
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getTitle(),
                evidence.getType(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getSupportedClaims(),
                evidence.getPublicStatus(),
                evidence.getRawContentPublic()
        );
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

    public boolean isRawContentPublic() {
        return rawContentPublic;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EvidenceResponse that)) {
            return false;
        }
        return sourceCount == that.sourceCount
                && rawContentPublic == that.rawContentPublic
                && Objects.equals(id, that.id)
                && Objects.equals(title, that.title)
                && type == that.type
                && Objects.equals(periodStart, that.periodStart)
                && Objects.equals(periodEnd, that.periodEnd)
                && Objects.equals(summary, that.summary)
                && Objects.equals(supportedClaims, that.supportedClaims)
                && publicStatus == that.publicStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, type, periodStart, periodEnd, sourceCount, summary,
                supportedClaims, publicStatus, rawContentPublic);
    }

    @Override
    public String toString() {
        return "EvidenceResponse{" +
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
