package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class EvidenceResponse {

    private final String id;
    private final String code;
    private final String title;
    private final String type;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int sourceCount;
    private final String summary;
    private final List<String> supportedClaims;
    private final String publicStatus;
    private final boolean rawContentPublic;
    private final List<String> projectSlugs;

    public EvidenceResponse(
            String id,
            String code,
            String title,
            String type,
            LocalDate periodStart,
            LocalDate periodEnd,
            int sourceCount,
            String summary,
            List<String> supportedClaims,
            String publicStatus,
            boolean rawContentPublic,
            List<String> projectSlugs
    ) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.type = type;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.sourceCount = sourceCount;
        this.summary = summary;
        this.supportedClaims = List.copyOf(supportedClaims);
        this.publicStatus = publicStatus;
        this.rawContentPublic = rawContentPublic;
        this.projectSlugs = List.copyOf(projectSlugs);
    }

    public static EvidenceResponse from(EvidenceRecord evidence) {
        return from(evidence, List.of());
    }

    public static EvidenceResponse from(EvidenceRecord evidence, List<String> projectSlugs) {
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getCode(),
                evidence.getTitle(),
                evidence.getType().name(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getSupportedClaims(),
                evidence.getPublicStatus().name(),
                evidence.getRawContentPublic(),
                projectSlugs
        );
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

    public List<String> getSupportedClaims() {
        return supportedClaims;
    }

    public String getPublicStatus() {
        return publicStatus;
    }

    public boolean isRawContentPublic() {
        return rawContentPublic;
    }

    public List<String> getProjectSlugs() {
        return projectSlugs;
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
                && Objects.equals(code, that.code)
                && Objects.equals(title, that.title)
                && Objects.equals(type, that.type)
                && Objects.equals(periodStart, that.periodStart)
                && Objects.equals(periodEnd, that.periodEnd)
                && Objects.equals(summary, that.summary)
                && Objects.equals(supportedClaims, that.supportedClaims)
                && Objects.equals(publicStatus, that.publicStatus)
                && Objects.equals(projectSlugs, that.projectSlugs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, code, title, type, periodStart, periodEnd, sourceCount, summary,
                supportedClaims, publicStatus, rawContentPublic, projectSlugs);
    }

    @Override
    public String toString() {
        return "EvidenceResponse{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", sourceCount=" + sourceCount +
                ", summary='" + summary + '\'' +
                ", supportedClaims=" + supportedClaims +
                ", publicStatus=" + publicStatus +
                ", rawContentPublic=" + rawContentPublic +
                ", projectSlugs=" + projectSlugs +
                '}';
    }
}
