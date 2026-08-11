package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Provenance of a successful renderable task result. */
public final class TaskResultProvenance {

    public enum DerivationType {
        DIRECT,
        SYNTHESIZED
    }

    private final DerivationType derivationType;
    private final Set<TaskSourceDomain> originDomains;
    private final List<String> sourceTaskIds;
    private final List<String> claimIds;
    private final List<String> evidenceIds;

    private TaskResultProvenance(
            DerivationType derivationType,
            Set<TaskSourceDomain> originDomains,
            List<String> sourceTaskIds,
            List<String> claimIds,
            List<String> evidenceIds) {
        this.derivationType = Objects.requireNonNull(derivationType, "derivationType");
        this.originDomains = copyDomains(originDomains);
        this.sourceTaskIds = copyIdentifiers(sourceTaskIds, "sourceTaskIds");
        this.claimIds = copyIdentifiers(claimIds, "claimIds");
        this.evidenceIds = copyIdentifiers(evidenceIds, "evidenceIds");
        validate();
    }

    public static TaskResultProvenance direct(
            TaskSourceDomain originDomain, List<String> claimIds, List<String> evidenceIds) {
        return new TaskResultProvenance(
                DerivationType.DIRECT,
                Set.of(Objects.requireNonNull(originDomain, "originDomain")),
                List.of(),
                claimIds,
                evidenceIds);
    }

    public static TaskResultProvenance synthesized(
            Set<TaskSourceDomain> originDomains,
            List<String> sourceTaskIds,
            List<String> claimIds,
            List<String> evidenceIds) {
        return new TaskResultProvenance(
                DerivationType.SYNTHESIZED, originDomains, sourceTaskIds, claimIds, evidenceIds);
    }

    public DerivationType getDerivationType() {
        return derivationType;
    }

    public Set<TaskSourceDomain> getOriginDomains() {
        return originDomains;
    }

    public List<String> getSourceTaskIds() {
        return sourceTaskIds;
    }

    public List<String> getClaimIds() {
        return claimIds;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskResultProvenance that)) {
            return false;
        }
        return derivationType == that.derivationType
                && Objects.equals(originDomains, that.originDomains)
                && Objects.equals(sourceTaskIds, that.sourceTaskIds)
                && Objects.equals(claimIds, that.claimIds)
                && Objects.equals(evidenceIds, that.evidenceIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(derivationType, originDomains, sourceTaskIds, claimIds, evidenceIds);
    }

    @Override
    public String toString() {
        return "TaskResultProvenance{derivationType=" + derivationType
                + ", originDomainCount=" + originDomains.size()
                + ", sourceTaskCount=" + sourceTaskIds.size()
                + ", claimCount=" + claimIds.size()
                + ", evidenceCount=" + evidenceIds.size() + '}';
    }

    private void validate() {
        if (originDomains.contains(TaskSourceDomain.SYNTHESIS)) {
            throw new IllegalArgumentException("originDomains may only contain factual source domains");
        }
        if (derivationType == DerivationType.DIRECT) {
            if (originDomains.size() != 1 || !sourceTaskIds.isEmpty()) {
                throw new IllegalArgumentException("direct provenance requires one origin domain and no source tasks");
            }
            return;
        }
        if (originDomains.isEmpty() || sourceTaskIds.size() < 2) {
            throw new IllegalArgumentException("synthesized provenance requires origins and at least two source tasks");
        }
    }

    private static Set<TaskSourceDomain> copyDomains(Set<TaskSourceDomain> originDomains) {
        Objects.requireNonNull(originDomains, "originDomains");
        return Set.copyOf(new LinkedHashSet<>(originDomains));
    }

    private static List<String> copyIdentifiers(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copied = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = requireText(value, name);
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException(name + " must not contain duplicate values");
            }
            copied.add(normalized);
        }
        return List.copyOf(copied);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
