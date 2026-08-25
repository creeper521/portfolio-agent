package com.portfolio.agent.evaluation.application;

import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.execution.EvalExecutionEngine;
import com.portfolio.agent.evaluation.execution.EvalRunContext;
import com.portfolio.agent.evaluation.execution.EvalRunPlan;
import com.portfolio.agent.evaluation.execution.EvalRunPlanner;
import com.portfolio.agent.evaluation.grading.EvalGrader;
import com.portfolio.agent.evaluation.grading.EvalGrade;
import com.portfolio.agent.evaluation.grading.EvalReasonCode;
import com.portfolio.agent.evaluation.reporting.EvalBaseline;
import com.portfolio.agent.evaluation.reporting.EvalBaselineComparator;
import com.portfolio.agent.evaluation.reporting.EvalComparison;
import com.portfolio.agent.evaluation.reporting.EvalGateResult;
import com.portfolio.agent.evaluation.reporting.EvalMetricAggregator;
import com.portfolio.agent.evaluation.reporting.EvalMetrics;
import com.portfolio.agent.evaluation.reporting.EvalRunReport;
import com.portfolio.agent.evaluation.reporting.EvalVerdictPolicy;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 评估流水线的应用层编排器：一次 {@link #run} 调用按顺序走完
 * 执行规划（{@link EvalRunPlanner}）→ 执行（{@link EvalExecutionEngine}）→
 * 评分（{@link EvalGrader}）→ 指标聚合（{@link EvalMetricAggregator}）→
 * baseline 对比（{@link EvalBaselineComparator}）→ 质量门（gate）生成 →
 * verdict 判定（{@link EvalVerdictPolicy}），最终产出不可变的 {@link EvalRunReport}。
 * 数据集与配置由调用方（CLI）提前装载完毕，本类不再做 IO。
 *
 * <p>关键不变量：整条流水线 fail-closed——空运行、缺失或零分母的指标不允许静默
 * PASS；PROVIDER 模式若没有产生任何 PROVIDER 层观测，无论授权状态如何都判
 * {@link EvalVerdict#INCOMPLETE}，而不是 PASS。</p>
 */
public final class EvalHarness {

    private final EvalExecutionEngine engine;
    private final EvalGrader grader;
    private final EvalMetricAggregator aggregator;
    private final EvalBaselineComparator baselineComparator;
    private final EvalVerdictPolicy verdictPolicy;
    private final RuntimeContentSnapshot bundle;
    private final EvalRunPlanner planner;

    public EvalHarness(
            List<com.portfolio.agent.evaluation.execution.EvalExecutor> executors,
            EvalGrader grader,
            EvalMetricAggregator aggregator,
            EvalBaselineComparator baselineComparator,
            EvalVerdictPolicy verdictPolicy,
            RuntimeContentSnapshot bundle) {
        this.engine = new EvalExecutionEngine(
                Objects.requireNonNull(executors, "executors"));
        this.grader = Objects.requireNonNull(grader, "grader");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
        this.baselineComparator = Objects.requireNonNull(baselineComparator, "baselineComparator");
        this.verdictPolicy = Objects.requireNonNull(verdictPolicy, "verdictPolicy");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.planner = new EvalRunPlanner();
    }

    /**
     * 执行一次完整评估运行并产出报告。
     *
     * <p>流程：用例按 id 排序保证确定性 → 规划受影响用例 → 执行引擎产生观测 →
     * 逐观测评分 → 聚合指标（并叠加 policy 回归上限伪指标）→ 可选 baseline 对比 →
     * 生成质量门 → 判定 {@link EvalVerdict}。副作用取决于注入的执行器：离线模式为
     * 纯内存计算，PROVIDER 模式可能触发外部模型调用。</p>
     *
     * @param suite 已装载的评估数据集；其 cases 为 {@code null} 时抛 {@link EvalRunException}
     * @param config 已通过构造校验的运行配置
     * @return 含指标、对比、质量门、逐观测评分与 verdict 的完整运行报告
     */
    public EvalRunReport run(EvalSuite suite, EvalRunConfig config) {
        Objects.requireNonNull(suite, "suite");
        Objects.requireNonNull(config, "config");
        if (suite.getCases() == null) {
            throw new EvalRunException("Evaluation suite has no cases");
        }
        String runId = "run-" + UUID.randomUUID();
        EvalRunContext context = new EvalRunContext(
                runId, bundle.getContentVersion(), config.getProviderAuthorization());

        List<EvalCase> sortedCases = new ArrayList<>(suite.getCases());
        sortedCases.sort(java.util.Comparator.comparing(EvalCase::getId));

        int publicSubjectCount = bundle.getProjects().size() + bundle.getCases().size();
        int coveredSubjectCount = coveredPublicSubjects(sortedCases);

        EvalRunPlan plan = planner.plan(
                config.getMode(), sortedCases, changedSubjects(config));
        List<EvalObservation> observations = engine.execute(plan, context);

        List<EvalGrade> grades = gradeAll(sortedCases, observations);
        EvalMetrics metrics = aggregator.aggregate(
                grades, observations, publicSubjectCount, coveredSubjectCount);
        metrics = withPolicyLimits(metrics, config);

        EvalComparison comparison = compare(config, metrics, sortedCases);
        List<EvalGateResult> gates = generateGates(metrics, config, observations,
                sortedCases.size(), sortedCases);
        EvalVerdict verdict;
        if (config.getMode() == EvalRunMode.PROVIDER
                && observations.stream().noneMatch(observation ->
                        observation.getLayer() == EvalLayer.PROVIDER)) {
            // provider mode without any executed trial is never a pass: the
            // real seam was not used (or was unavailable), so the run is
            // INCOMPLETE regardless of authorization
            verdict = EvalVerdict.INCOMPLETE;
        } else {
            verdict = verdictPolicy.decide(
                    config.getMode(), metrics, comparison, gates,
                    config.getProviderAuthorization());
        }

        return new EvalRunReport(
                runId, config.getMode(), config.getIdentity(), verdict,
                metrics, comparison, gates, observations, grades,
                config.getBaseline().map(EvalBaseline::getBaselineId),
                config.getProviderAuthorization());
    }

    /**
     * 将每条观测回联到所属用例并交给 {@link EvalGrader} 评分；
     * 找不到对应用例的观测直接跳过（不计分），评分结果按观测顺序保持稳定。
     */
    private List<EvalGrade> gradeAll(
            List<EvalCase> cases,
            List<EvalObservation> observations) {
        Map<String, EvalCase> byId = new HashMap<>();
        for (EvalCase evalCase : cases) {
            byId.put(evalCase.getId(), evalCase);
        }
        List<EvalGrade> grades = new ArrayList<>();
        for (EvalObservation observation : observations) {
            EvalCase evalCase = byId.get(observation.getCaseId());
            if (evalCase != null) {
                grades.addAll(grader.grade(evalCase, observation));
            }
        }
        return List.copyOf(grades);
    }

    /**
     * 统计数据集 expectedSubjects 覆盖到的去重公开主体数（以 type:slug 为去重键），
     * 作为 coverage 指标的分子来源；expectedSubjects 为 null 的用例不参与统计。
     */
    private int coveredPublicSubjects(List<EvalCase> cases) {
        Set<String> covered = new HashSet<>();
        for (EvalCase evalCase : cases) {
            if (evalCase.getExpectedSubjects() == null) {
                continue;
            }
            for (EvalSubjectRef subject : evalCase.getExpectedSubjects()) {
                covered.add(subject.getType().name() + ":" + subject.getSlug());
            }
        }
        return covered.size();
    }

    /**
     * 将配置中以 type → slug 集合表示的变更主体表转换为 {@link EvalSubjectRef} 集合，
     * 供规划器判定哪些用例受本次内容变更影响；type 字符串必须是合法的
     * ClaimSubjectType 名称，否则抛 {@link IllegalArgumentException}。
     */
    private Set<EvalSubjectRef> changedSubjects(EvalRunConfig config) {
        Set<EvalSubjectRef> subjects = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : config.getChangedSubjects().entrySet()) {
            String type = entry.getKey();
            for (String slug : entry.getValue()) {
                subjects.add(new EvalSubjectRef(
                        com.portfolio.agent.portfolio.domain.ClaimSubjectType.valueOf(type),
                        slug));
            }
        }
        return subjects;
    }

    /**
     * 在聚合指标之上追加两条 policy 回归上限伪指标（优先级指标与全局指标允许的
     * baseline 回退上限），使后续 baseline 对比与报告能直接引用统一的指标命名空间。
     */
    private EvalMetrics withPolicyLimits(EvalMetrics metrics, EvalRunConfig config) {
        Map<String, EvalMetrics.MetricValue> values = new LinkedHashMap<>(metrics.getAll());
        values.put("policy.priorityRegressionLimit", new EvalMetrics.MetricValue(
                config.getPolicy().getPriorityMetricRegressionMaximum(), 2L, 100L));
        values.put("policy.globalRegressionLimit", new EvalMetrics.MetricValue(
                config.getPolicy().getGlobalMetricRegressionMaximum(), 3L, 100L));
        return new EvalMetrics(values);
    }

    /**
     * 与配置中的 baseline 比较；未配置 baseline 时返回 notComparable 的占位结果
     * 而不是抛错，由报告层如实呈现"不可比"。
     */
    private EvalComparison compare(
            EvalRunConfig config,
            EvalMetrics metrics,
            List<EvalCase> cases) {
        if (config.getBaseline().isEmpty()) {
            return EvalComparison.notComparable();
        }
        List<String> caseIds = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            caseIds.add(evalCase.getId());
        }
        return baselineComparator.compare(
                config.getIdentity(), metrics, config.getBaseline().get(), caseIds);
    }

    /**
     * 依据运行模式生成 BLOCKING 质量门，全部参与 verdict 判定。
     *
     * <p>零观测时按模式 fail-closed：VALIDATE 只验数据集自身（非空、引用完整、
     * id 唯一、smoke coverage 达标）；PROVIDER 返回空门列表（由 {@link #run}
     * 直接判 INCOMPLETE）；其余离线模式要求至少执行过一条用例。</p>
     *
     * <p>有观测时按 policy 阈值挂 hardError、smoke coverage（仅当数据集含
     * BUNDLE_GENERATED 生成的 smoke 用例，纯手写数据集无此契约）、routing、
     * retrieval、safety、API 契约门；PROVIDER 模式追加 trial 通过率、错误率与
     * p95 延迟门。</p>
     */
    private List<EvalGateResult> generateGates(
            EvalMetrics metrics,
            EvalRunConfig config,
            List<EvalObservation> observations,
            int datasetCaseCount,
            List<EvalCase> suiteCases) {
        if (observations.isEmpty()) {
            // Fail-closed: an empty run is never silently PASS.
            List<EvalGateResult> gates = new ArrayList<>();
            if (config.getMode() == EvalRunMode.VALIDATE) {
                // validate does not execute cases; it must still prove that the
                // dataset is non-empty, covers every public subject and carries
                // only well-formed references
                gates.add(datasetGate(datasetCaseCount));
                EvalMetrics.MetricValue coverage =
                        metrics.getValue("content.smokeCoverage");
                if (coverage != null && coverage.getDenominator() > 0L) {
                    gates.add(gateResult(metrics, "content.smokeCoverage",
                            config.getPolicy().getPublicSubjectSmokeCoverageMinimum(),
                            EvalGateResult.EvalComparisonOperator.GE));
                }
                gates.add(referenceIntegrityGate(suiteCases, bundle));
                gates.add(duplicateCaseIdGate(suiteCases));
            } else if (config.getMode() == EvalRunMode.PROVIDER) {
                // provider mode with no executed trials is INCOMPLETE, not
                // FAIL: neither the mock nor a real seam produced observations
                return List.of();
            } else {
                // Offline runs must have executed at least one case;
                // an all-skipped or empty run is a failure, not a pass
                gates.add(gateResult(metrics, "run.executedCaseCount", BigDecimal.ONE,
                        EvalGateResult.EvalComparisonOperator.GE));
            }
            return List.copyOf(gates);
        }
        List<EvalGateResult> gates = new ArrayList<>();
        addGate(gates, metrics, "hardError.count", BigDecimal.ZERO,
                EvalGateResult.EvalComparisonOperator.LE, EvalSeverity.BLOCKING,
                EvalReasonCode.EXECUTOR_ERROR);
        // smoke coverage is a contract of suites that expand generated smoke
        // cases; handwritten-only suites have no such contract
        boolean hasGeneratedSmoke = suiteCases.stream().anyMatch(evalCase ->
                evalCase.getOrigin()
                == com.portfolio.agent.evaluation.domain.EvalOrigin.BUNDLE_GENERATED);
        if (hasGeneratedSmoke) {
            addGate(gates, metrics, "content.smokeCoverage",
                    config.getPolicy().getPublicSubjectSmokeCoverageMinimum(),
                    EvalGateResult.EvalComparisonOperator.GE, EvalSeverity.BLOCKING,
                    EvalReasonCode.GATE_NOT_MET);
        }
        addGate(gates, metrics, "routing.top1",
                config.getPolicy().getDeepSemanticRouteTopOneMinimum(),
                EvalGateResult.EvalComparisonOperator.GE, EvalSeverity.BLOCKING,
                EvalReasonCode.GATE_NOT_MET);
        addGate(gates, metrics, "retrieval.hitAt5",
                config.getPolicy().getRetrievalHitAtFiveMinimum(),
                EvalGateResult.EvalComparisonOperator.GE, EvalSeverity.BLOCKING,
                EvalReasonCode.GATE_NOT_MET);
        addGate(gates, metrics, "retrieval.claimRecall",
                config.getPolicy().getRequiredClaimRecallMinimum(),
                EvalGateResult.EvalComparisonOperator.GE, EvalSeverity.BLOCKING,
                EvalReasonCode.GATE_NOT_MET);
        addGate(gates, metrics, "safety.boundaryPassRate",
                config.getPolicy().getSafetyBoundaryPassRateMinimum(),
                EvalGateResult.EvalComparisonOperator.GE, EvalSeverity.BLOCKING,
                EvalReasonCode.GATE_NOT_MET);
        addGate(gates, metrics, "api.contractPassRate", BigDecimal.ONE,
                EvalGateResult.EvalComparisonOperator.GE, EvalSeverity.BLOCKING,
                EvalReasonCode.GATE_NOT_MET);
        if (config.getMode() == EvalRunMode.PROVIDER) {
            addGate(gates, metrics, "provider.trialPassRate",
                    config.getPolicy().getProviderTrialPassRateMinimum(),
                    EvalGateResult.EvalComparisonOperator.GE, EvalSeverity.BLOCKING,
                    EvalReasonCode.GATE_NOT_MET);
            addGate(gates, metrics, "provider.errorRate",
                    config.getPolicy().getProviderFailureRateMaximum(),
                    EvalGateResult.EvalComparisonOperator.LE, EvalSeverity.BLOCKING,
                    EvalReasonCode.GATE_NOT_MET);
            addGate(gates, metrics, "provider.p95LatencyMs",
                    BigDecimal.valueOf(config.getPolicy().getProviderP95LatencyMaximumMs()),
                    EvalGateResult.EvalComparisonOperator.LE, EvalSeverity.BLOCKING,
                    EvalReasonCode.GATE_NOT_MET);
        }
        return List.copyOf(gates);
    }

    /**
     * Reference integrity gate for validate: every subject ref, required claim
     * and allowed evidence id in every case must exist in the public bundle.
     * Violations are counted and must be zero.
     */
    private EvalGateResult referenceIntegrityGate(
            List<EvalCase> cases,
            RuntimeContentSnapshot bundle) {
        java.util.Set<String> subjects = new HashSet<>();
        for (com.portfolio.agent.portfolio.domain.ProjectProfile project
                : bundle.getProjects()) {
            subjects.add(project.getSlug());
        }
        for (com.portfolio.agent.portfolio.domain.CaseStudy caseSubject
                : bundle.getCases()) {
            subjects.add(caseSubject.getSlug());
        }
        java.util.Set<String> claims = new HashSet<>();
        for (com.portfolio.agent.portfolio.domain.Claim claim : bundle.getClaims()) {
            claims.add(claim.getId());
        }
        java.util.Set<String> evidence = new HashSet<>();
        for (com.portfolio.agent.portfolio.domain.EvidenceRecord record
                : bundle.getApprovedEvidence()) {
            evidence.add(record.getId());
        }
        long violations = 0;
        for (EvalCase evalCase : cases) {
            for (EvalSubjectRef ref : evalCase.getExpectedSubjects()) {
                if (!subjects.contains(ref.getSlug())) {
                    violations++;
                }
            }
            for (EvalSubjectRef ref : evalCase.getMaintenanceSubjects()) {
                if (!subjects.contains(ref.getSlug())) {
                    violations++;
                }
            }
            for (String claimId : evalCase.getRequiredClaimIds()) {
                if (!claims.contains(claimId)) {
                    violations++;
                }
            }
            for (String evidenceId : evalCase.getAllowedEvidenceIds()) {
                if (!evidence.contains(evidenceId)) {
                    violations++;
                }
            }
        }
        BigDecimal observed = BigDecimal.valueOf(violations);
        boolean passed = observed.compareTo(BigDecimal.ZERO) == 0;
        return new EvalGateResult(
                "dataset.referenceViolations", observed, BigDecimal.ZERO,
                EvalGateResult.EvalComparisonOperator.LE,
                passed, EvalSeverity.BLOCKING,
                passed ? EvalReasonCode.PASS : EvalReasonCode.GATE_NOT_MET);
    }

    /**
     * Duplicate case id gate for validate: ids must be unique within the suite.
     */
    private EvalGateResult duplicateCaseIdGate(List<EvalCase> cases) {
        java.util.Set<String> seen = new HashSet<>();
        long duplicates = 0;
        for (EvalCase evalCase : cases) {
            if (!seen.add(evalCase.getId())) {
                duplicates++;
            }
        }
        BigDecimal observed = BigDecimal.valueOf(duplicates);
        boolean passed = observed.compareTo(BigDecimal.ZERO) == 0;
        return new EvalGateResult(
                "dataset.duplicateCaseIds", observed, BigDecimal.ZERO,
                EvalGateResult.EvalComparisonOperator.LE,
                passed, EvalSeverity.BLOCKING,
                passed ? EvalReasonCode.PASS : EvalReasonCode.GATE_NOT_MET);
    }

    /**
     * Non-empty dataset gate for validate: observed value is the dataset case
     * count itself, so an empty dataset always fails.
     */
    private EvalGateResult datasetGate(int datasetCaseCount) {
        BigDecimal observed = BigDecimal.valueOf(datasetCaseCount);
        boolean passed = observed.compareTo(BigDecimal.ONE) >= 0;
        return new EvalGateResult(
                "dataset.caseCount", observed, BigDecimal.ONE,
                EvalGateResult.EvalComparisonOperator.GE,
                passed, EvalSeverity.BLOCKING,
                passed ? EvalReasonCode.PASS : EvalReasonCode.GATE_NOT_MET);
    }

    /**
     * Always-visible gate that never skips on a zero denominator: a missing
     * metric or an empty denominator is observed as 0.0 and fails the gate,
     * keeping empty runs fail-closed.
     */
    private EvalGateResult gateResult(
            EvalMetrics metrics,
            String metricName,
            BigDecimal threshold,
            EvalGateResult.EvalComparisonOperator comparison) {
        EvalMetrics.MetricValue value = metrics.getValue(metricName);
        BigDecimal observed = value == null || value.getDenominator() == 0L
                ? BigDecimal.ZERO
                : value.getValue();
        boolean passed = switch (comparison) {
            case GE -> observed.compareTo(threshold) >= 0;
            case LE -> observed.compareTo(threshold) <= 0;
            case EXACT -> observed.compareTo(threshold) == 0;
        };
        return new EvalGateResult(
                metricName, observed, threshold, comparison,
                passed, EvalSeverity.BLOCKING,
                passed ? EvalReasonCode.PASS : EvalReasonCode.GATE_NOT_MET);
    }

    /**
     * 向门列表追加一个阈值门：指标缺失或分母为 0 的门直接跳过
     * （hardError.count 例外，它没有分母语义且必须始终参与判定），
     * 避免空数据集把本应失败的门误判为通过。
     */
    private void addGate(
            List<EvalGateResult> gates,
            EvalMetrics metrics,
            String metricName,
            BigDecimal threshold,
            EvalGateResult.EvalComparisonOperator comparison,
            EvalSeverity severity,
            EvalReasonCode failReason) {
        EvalMetrics.MetricValue value = metrics.getValue(metricName);
        if (value == null || value.getDenominator() == 0L
                && !metricName.equals("hardError.count")) {
            return;
        }
        boolean passed = switch (comparison) {
            case GE -> value.getValue().compareTo(threshold) >= 0;
            case LE -> value.getValue().compareTo(threshold) <= 0;
            case EXACT -> value.getValue().compareTo(threshold) == 0;
        };
        gates.add(new EvalGateResult(
                metricName, value.getValue(), threshold, comparison,
                passed, severity, passed ? EvalReasonCode.PASS : failReason));
    }
}
