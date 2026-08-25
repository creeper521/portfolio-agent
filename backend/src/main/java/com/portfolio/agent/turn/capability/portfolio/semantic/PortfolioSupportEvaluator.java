package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 语义支撑评估器（无状态）：按 Goal 的 facet/维度从已验证 Evidence 束中选择支撑单元并声明遗漏。
 *
 * <p>选择规则：事实评估每个 facet 取首个命中类别的单元（每 facet 至多一条）；
 * 对比评估按维度×主体矩阵取全部命中。遗漏以稳定字符串（facet 名或 主体/维度）记录，
 * 用于决定 Coverage 是 FULL 还是 PARTIAL。claim 类别与 facet/维度的映射固定，
 * 未知维度抛出 IllegalArgumentException（fail-closed）。
 */
public final class PortfolioSupportEvaluator {
    /**
     * 评估事实型（Fact）Goal 的支撑：逐 facet 匹配 claim 类别，未命中记入 omissions。
     *
     * @param invocation 当前 Evidence 调用（提供 facet 列表）
     * @param bundle     已通过晋级校验的 Evidence 束
     * @return 支撑评估结果，含选中的单元与遗漏清单
     */
    public Evaluation fact(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        List<ValidatedEvidenceUnit> selected = new ArrayList<>();
        List<String> omissions = new ArrayList<>();
        for (PortfolioEvidenceInvocation.FacetProfile facet : invocation.getFacets()) {
            List<ValidatedEvidenceUnit> matches = bundle.getUnits().stream()
                    .filter(unit -> categories(facet).contains(unit.getClaim().getCategory())).toList();
            if (matches.isEmpty()) omissions.add(facet.name());
            else addDistinct(selected, matches.getFirst());
        }
        return Evaluation.of(selected, omissions);
    }

    /**
     * 评估对比型（Comparison）Goal 的支撑：按维度×获准主体逐一匹配，未命中记为 主体/维度 遗漏。
     *
     * @param invocation 当前 Evidence 调用（提供对比维度与主体范围）
     * @param bundle     已通过晋级校验的 Evidence 束
     * @return 支撑评估结果
     */
    public Evaluation comparison(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        List<ValidatedEvidenceUnit> selected = new ArrayList<>();
        List<String> omissions = new ArrayList<>();
        for (String dimension : invocation.getDimensions()) {
            Set<AnswerClaimCategory> categories = dimensionCategories(dimension);
            for (AuthorizedSubject subject : subjects(invocation)) {
                List<ValidatedEvidenceUnit> matches = bundle.getUnits().stream()
                        .filter(unit -> unit.getSubjectId().equals(subject.id)
                                && categories.contains(unit.getClaim().getCategory())).toList();
                if (matches.isEmpty()) omissions.add(subject.id + "/" + dimension);
                else matches.forEach(unit -> addDistinct(selected, unit));
            }
        }
        return Evaluation.of(selected, omissions);
    }

    private List<AuthorizedSubject> subjects(PortfolioEvidenceInvocation invocation) {
        return invocation.getSubjectScope().getSubjects().stream()
                .map(value -> new AuthorizedSubject(value.getReference())).toList();
    }

    /** facet 到 claim 类别的固定映射；RECOMMENDATION 面向全部类别。 */
    private Set<AnswerClaimCategory> categories(PortfolioEvidenceInvocation.FacetProfile facet) {
        return switch (facet) {
            case BACKGROUND -> EnumSet.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> EnumSet.of(AnswerClaimCategory.RESPONSIBILITY);
            case IMPLEMENTATION -> EnumSet.of(AnswerClaimCategory.IMPLEMENTATION);
            case TECHNICAL_DECISION -> EnumSet.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case VERIFICATION -> EnumSet.of(AnswerClaimCategory.VERIFICATION);
            case OUTCOME -> EnumSet.of(AnswerClaimCategory.OUTCOME);
            case LIMITATION -> EnumSet.of(AnswerClaimCategory.LIMITATION);
            case RECOMMENDATION -> EnumSet.allOf(AnswerClaimCategory.class);
        };
    }

    /** 对比维度到 claim 类别的固定映射；未知维度直接抛异常，禁止静默降级。 */
    private Set<AnswerClaimCategory> dimensionCategories(String dimension) {
        return switch (dimension) {
            case "ARCHITECTURE" ->
                    EnumSet.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case "IMPLEMENTATION" -> EnumSet.of(AnswerClaimCategory.IMPLEMENTATION);
            case "OUTCOME" -> EnumSet.of(AnswerClaimCategory.OUTCOME);
            case "RISKS" -> EnumSet.of(AnswerClaimCategory.LIMITATION);
            case "VERIFICATION" -> EnumSet.of(AnswerClaimCategory.VERIFICATION);
            default -> throw new IllegalArgumentException(
                    "unsupported portfolio comparison dimension");
        };
    }

    /** 按 claimId + 来源引用键去重后追加，避免同一证据在不同 facet/维度下重复入选。 */
    private void addDistinct(List<ValidatedEvidenceUnit> target, ValidatedEvidenceUnit unit) {
        boolean exists = target.stream().anyMatch(value ->
                value.getClaim().getId().equals(unit.getClaim().getId())
                        && value.getSourceReference().getReferenceKey().equals(
                        unit.getSourceReference().getReferenceKey()));
        if (!exists) target.add(unit);
    }

    /** 支撑评估结果（不可变）：选中的 Evidence 单元与去重后的遗漏清单，覆盖度由遗漏是否为空推导。 */
    public static final class Evaluation {
        private final List<ValidatedEvidenceUnit> selectedUnits;
        private final List<String> omissions;
        private Evaluation(List<ValidatedEvidenceUnit> selectedUnits, List<String> omissions) {
            this.selectedUnits = List.copyOf(selectedUnits);
            this.omissions = List.copyOf(new LinkedHashSet<>(omissions));
        }
        static Evaluation of(List<ValidatedEvidenceUnit> units, List<String> omissions) {
            return new Evaluation(units, omissions);
        }
        public boolean hasSupport() { return !selectedUnits.isEmpty(); }
        public PortfolioSemanticResult.Coverage coverage() {
            return omissions.isEmpty() ? PortfolioSemanticResult.Coverage.FULL
                    : PortfolioSemanticResult.Coverage.PARTIAL;
        }
        public List<ValidatedEvidenceUnit> getSelectedUnits() { return selectedUnits; }
        public List<String> getOmissions() { return omissions; }
    }

    /** 内部主体标识载体，仅携带获准主体引用。 */
    private static final class AuthorizedSubject {
        private final String id;
        private AuthorizedSubject(String id) { this.id = id; }
    }
}
