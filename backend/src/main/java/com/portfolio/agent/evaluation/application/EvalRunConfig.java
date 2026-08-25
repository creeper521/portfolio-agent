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

/**
 * 单次评估运行（{@link EvalHarness#run}）的不可变配置：运行模式
 * （{@link EvalRunMode}）、运行身份、阈值策略（{@link EvalPolicy}）、
 * 变更主体表、可选 baseline、Provider 授权与离线前置 verdict。
 *
 * <p>关键不变量：构造时对 changedSubjects 做防御性拷贝并整体只读，随后校验
 * 组合约束——PROVIDER 模式必须携带已 PASS 的离线前置 verdict 且至少具备
 * mock 级授权；非 PROVIDER 模式禁止携带真实 Provider 授权。违反约束在构造期
 * 抛出 {@link IllegalArgumentException}，保证进入流水线的配置天然合法。</p>
 */
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

    /**
     * 校验模式、授权与前置 verdict 的组合约束，是本配置类唯一的不变量来源；
     * 不满足时构造即失败，避免带病配置流入评估流水线。
     */
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
