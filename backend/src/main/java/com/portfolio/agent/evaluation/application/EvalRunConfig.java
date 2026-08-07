package com.portfolio.agent.evaluation.application;

import com.portfolio.agent.evaluation.domain.EvalPolicy;
import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;
import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.reporting.EvalBaseline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EvalRunConfig {

    private final EvalRunMode mode;
    private final EvalRunIdentity identity;
    private final EvalPolicy policy;
    private final Map<String, Set<String>> changedSubjects;
    private final Optional<EvalBaseline> baseline;
    private final EvalProviderAuthorization providerAuthorization;
    private final Optional<EvalVerdict> offlinePrerequisiteVerdict;

    public EvalRunConfig(
            EvalRunMode mode,
            EvalRunIdentity identity,
            EvalPolicy policy,
            Map<String, Set<String>> changedSubjects,
            Optional<EvalBaseline> baseline,
            EvalProviderAuthorization providerAuthorization,
            Optional<EvalVerdict> offlinePrerequisiteVerdict) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.policy = Objects.requireNonNull(policy, "policy");
        Map<String, Set<String>> defensive = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry
                : Objects.requireNonNull(changedSubjects, "changedSubjects").entrySet()) {
            defensive.put(entry.getKey(),
                    Collections.unmodifiableSet(new java.util.LinkedHashSet<>(
                            Objects.requireNonNull(entry.getValue(), "changed subject set"))));
        }
        this.changedSubjects = Collections.unmodifiableMap(defensive);
        this.baseline = Objects.requireNonNull(baseline, "baseline");
        this.providerAuthorization = Objects.requireNonNull(
                providerAuthorization, "providerAuthorization");
        this.offlinePrerequisiteVerdict = Objects.requireNonNull(
                offlinePrerequisiteVerdict, "offlinePrerequisiteVerdict");
        validate();
    }

    private void validate() {
        if (mode == EvalRunMode.PROVIDER) {
            if (offlinePrerequisiteVerdict.isEmpty()) {
                throw new IllegalArgumentException(
                        "Provider mode requires an offline prerequisite verdict");
            }
            if (offlinePrerequisiteVerdict.get() != EvalVerdict.PASS) {
                throw new IllegalArgumentException(
                        "Provider mode requires the offline prerequisite to pass");
            }
            if (providerAuthorization == EvalProviderAuthorization.NOT_AUTHORIZED) {
                throw new IllegalArgumentException(
                        "Provider mode requires at least mock-only authorization");
            }
        } else {
            if (providerAuthorization == EvalProviderAuthorization.REAL_AUTHORIZED) {
                throw new IllegalArgumentException(
                        "Real provider authorization is only valid in provider mode");
            }
        }
    }

    public EvalRunMode getMode() { return mode; }
    public EvalRunIdentity getIdentity() { return identity; }
    public EvalPolicy getPolicy() { return policy; }
    public Map<String, Set<String>> getChangedSubjects() { return changedSubjects; }
    public Optional<EvalBaseline> getBaseline() { return baseline; }
    public EvalProviderAuthorization getProviderAuthorization() { return providerAuthorization; }
    public Optional<EvalVerdict> getOfflinePrerequisiteVerdict() { return offlinePrerequisiteVerdict; }
}
