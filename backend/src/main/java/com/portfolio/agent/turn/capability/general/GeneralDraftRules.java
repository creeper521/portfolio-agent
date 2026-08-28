package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Objects;

/** Compiler 与 Validator 共用的 General Draft 确定性规则表。 */
public final class GeneralDraftRules {
    private GeneralDraftRules() { }

    public static ExplanationRule explanation(
            UserGoalProposal.Depth depth) {
        return switch (Objects.requireNonNull(depth, "depth")) {
            case CONCISE -> new ExplanationRule(
                    1, 1, 2, 2,
                    List.of(GeneralDraftCodec.Aspect.DEFINITION),
                    List.of(GeneralDraftCodec.Aspect.MECHANISM));
            case STANDARD -> new ExplanationRule(
                    1, 3, 2, 6,
                    List.of(GeneralDraftCodec.Aspect.DEFINITION),
                    List.of(GeneralDraftCodec.Aspect.MECHANISM));
            case DETAILED -> new ExplanationRule(
                    4, 6, 8, 12,
                    List.of(GeneralDraftCodec.Aspect.DEFINITION),
                    List.of(GeneralDraftCodec.Aspect.MECHANISM));
        };
    }

    public static String topic(GeneralKnowledgeRequest request) {
        GeneralKnowledgeRequest required = Objects.requireNonNull(
                request, "request");
        return required.getKind() == GeneralKnowledgeRequest.Kind.EXPLANATION
                ? required.getTopic()
                : String.join(" vs ", required.getSubjects());
    }

    /** 中文为主：至少一个汉字，且汉字数量不得少于拉丁字母数量。 */
    public static boolean isChineseDominant(String value) {
        Objects.requireNonNull(value, "value");
        int han = 0;
        int latin = 0;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN) {
                han++;
            } else if (script == Character.UnicodeScript.LATIN
                    && Character.isLetter(codePoint)) {
                latin++;
            }
        }
        return han > 0 && han >= latin;
    }

    public record ExplanationRule(
            int minimumSentencesPerRole,
            int maximumSentencesPerRole,
            int minimumCanonicalSentences,
            int maximumCanonicalSentences,
            List<GeneralDraftCodec.Aspect> definitionAspects,
            List<GeneralDraftCodec.Aspect> mechanismAspects) {
        public ExplanationRule {
            definitionAspects = List.copyOf(definitionAspects);
            mechanismAspects = List.copyOf(mechanismAspects);
            if (minimumSentencesPerRole < 1
                    || maximumSentencesPerRole < minimumSentencesPerRole
                    || minimumCanonicalSentences
                    < Math.multiplyExact(2, minimumSentencesPerRole)
                    || maximumCanonicalSentences
                    > Math.multiplyExact(2, maximumSentencesPerRole)) {
                throw new IllegalArgumentException("explanation rule is invalid");
            }
        }
    }
}
