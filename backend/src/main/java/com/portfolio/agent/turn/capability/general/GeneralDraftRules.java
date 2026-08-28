package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiler 与 Validator 共用的 General Draft 确定性规则表。 */
public final class GeneralDraftRules {
    private GeneralDraftRules() { }

    public static ExplanationRule explanation(
            UserGoalProposal.Depth depth) {
        return switch (Objects.requireNonNull(depth, "depth")) {
            case CONCISE -> new ExplanationRule(
                    1, 2, 2,
                    List.of(GeneralDraftCodec.Aspect.DEFINITION),
                    List.of(GeneralDraftCodec.Aspect.MECHANISM));
            case STANDARD -> new ExplanationRule(
                    2, 4, 6,
                    List.of(
                            GeneralDraftCodec.Aspect.DEFINITION,
                            GeneralDraftCodec.Aspect.TYPICAL_USAGE),
                    List.of(
                            GeneralDraftCodec.Aspect.MECHANISM,
                            GeneralDraftCodec.Aspect.APPLICABILITY_BOUNDARY));
            case DETAILED -> new ExplanationRule(
                    4, 8, 12,
                    List.of(
                            GeneralDraftCodec.Aspect.DEFINITION,
                            GeneralDraftCodec.Aspect.TYPICAL_USAGE,
                            GeneralDraftCodec.Aspect.COMMON_MISCONCEPTION),
                    List.of(
                            GeneralDraftCodec.Aspect.MECHANISM,
                            GeneralDraftCodec.Aspect.APPLICABILITY_BOUNDARY,
                            GeneralDraftCodec.Aspect.TRADE_OFF,
                            GeneralDraftCodec.Aspect.BOUNDARY_CONDITION));
        };
    }

    public static String topic(GeneralKnowledgeRequest request) {
        GeneralKnowledgeRequest required = Objects.requireNonNull(
                request, "request");
        return required.getKind() == GeneralKnowledgeRequest.Kind.EXPLANATION
                ? required.getTopic()
                : String.join(" vs ", required.getSubjects());
    }

    public record ExplanationRule(
            int providerSentencesPerRole,
            int minimumCanonicalSentences,
            int maximumCanonicalSentences,
            List<GeneralDraftCodec.Aspect> definitionAspects,
            List<GeneralDraftCodec.Aspect> mechanismAspects) {
        public ExplanationRule {
            definitionAspects = List.copyOf(definitionAspects);
            mechanismAspects = List.copyOf(mechanismAspects);
        }

        public Set<GeneralDraftCodec.Aspect> coverage() {
            EnumSet<GeneralDraftCodec.Aspect> result = EnumSet.noneOf(
                    GeneralDraftCodec.Aspect.class);
            result.addAll(definitionAspects);
            result.addAll(mechanismAspects);
            return Set.copyOf(result);
        }
    }
}
