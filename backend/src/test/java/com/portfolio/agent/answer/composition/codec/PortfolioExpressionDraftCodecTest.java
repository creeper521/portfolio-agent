package com.portfolio.agent.answer.composition.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.domain.draft.ComparisonExpressionDraft;
import com.portfolio.agent.answer.composition.domain.draft.RecommendationExpressionDraft;
import org.junit.jupiter.api.Test;

class PortfolioExpressionDraftCodecTest {
    private final PortfolioExpressionDraftCodec codec = new PortfolioExpressionDraftCodec();
    private static final String VALID = """
            {"schemaVersion":"portfolio-expression-draft.v1","materialKind":"FACT","summary":null,
             "sections":[{"sectionType":"VERIFICATION","sentences":[{"text":"阶段性验证 42%","supports":["S001"]}]}]}
            """;
    private static final String VALID_COMPARISON = """
            {"schemaVersion":"portfolio-expression-draft.v1","materialKind":"COMPARISON",
             "intro":{"text":"比较说明","supports":["S001"]},
             "dimensions":[{"dimensionKey":"D01","subjects":[
               {"subjectKey":"P01","sentences":[{"text":"主体一事实","supports":["S001"]}]},
               {"subjectKey":"P02","sentences":[{"text":"主体二事实","supports":["S002"]}]}
             ],"comparisonSentences":[{"text":"受控比较关系","supports":["S003"]}]}]}
            """;
    private static final String VALID_RECOMMENDATION = """
            {"schemaVersion":"portfolio-expression-draft.v1","materialKind":"RECOMMENDATION",
             "intro":{"text":"推荐说明","supports":["S001"]},
             "items":[{"candidateKey":"C01","sentences":[
               {"text":"候选事实","supports":["S001"]}]}]}
            """;

    @Test void decodesExactFactShape() {
        FactExpressionDraft draft = (FactExpressionDraft) codec.decode(VALID, MaterialKind.FACT);
        assertThat(draft.getSummary()).isNull();
        assertThat(draft.getSections()).hasSize(1);
        assertThat(draft.allBodySentences().get(0).getSupports()).containsExactly("S001");
    }

    @Test void rejectsUnknownDuplicateCrossKindAndUnsafeText() {
        assertRejected(VALID.replace("\"summary\":null", "\"summary\":null,\"unknown\":1"));
        assertRejected(VALID.replace("\"materialKind\":\"FACT\"", "\"materialKind\":\"FACT\",\"materialKind\":\"FACT\""));
        assertRejected(VALID.replace("\"FACT\"", "\"COMPARISON\""));
        assertRejected(VALID.replace("阶段性验证 42%", "https://evil.invalid"));
        assertRejected(VALID.replace("S001", "P01"));
        assertRejected(VALID.replace("[\"S001\"]", "[\"S001\",\"S001\"]"));
        assertRejected(VALID.replace("阶段性验证 42%", "阶段性\t验证 42%"));
        assertRejected(VALID.replace("阶段性验证 42%", "`阶段性验证`"));
        assertRejected(VALID.replace("阶段性验证 42%", "> 阶段性验证"));
        assertRejected(VALID.replace("阶段性验证 42%", "mailto:test@example.com"));
        assertRejected(VALID.replace("阶段性验证 42%", "<b>阶段性验证</b>"));
        assertRejected(VALID.replace("\"sections\":[", "\"sections\":[] ,\"unused\":["));
    }

    @Test void decodesClosedComparisonAndRecommendationContracts() {
        ComparisonExpressionDraft comparison = (ComparisonExpressionDraft) codec.decode(
                VALID_COMPARISON, MaterialKind.COMPARISON);
        RecommendationExpressionDraft recommendation = (RecommendationExpressionDraft) codec.decode(
                VALID_RECOMMENDATION, MaterialKind.RECOMMENDATION);
        assertThat(comparison.getDimensions()).hasSize(1);
        assertThat(comparison.getDimensions().get(0).getSubjects()).hasSize(2);
        assertThat(recommendation.getItems()).extracting(
                RecommendationExpressionDraft.RecommendationDraftItem::getCandidateKey)
                .containsExactly("C01");
    }

    @Test void rejectsSectionSentenceAndTotalCharacterLimits() {
        String fiveSentences = """
                {"schemaVersion":"portfolio-expression-draft.v1","materialKind":"FACT","summary":null,
                 "sections":[{"sectionType":"VERIFICATION","sentences":[
                   {"text":"一","supports":["S001"]},{"text":"二","supports":["S001"]},
                   {"text":"三","supports":["S001"]},{"text":"四","supports":["S001"]},
                   {"text":"五","supports":["S001"]}]}]}
                """;
        assertRejected(fiveSentences);
        String longText = "甲".repeat(500);
        String overTotal = """
                {"schemaVersion":"portfolio-expression-draft.v1","materialKind":"FACT","summary":null,
                 "sections":[
                   {"sectionType":"BACKGROUND","sentences":[{"text":"%s","supports":["S001"]}]},
                   {"sectionType":"RESPONSIBILITY","sentences":[{"text":"%s","supports":["S001"]}]},
                   {"sectionType":"SOLUTION","sentences":[{"text":"%s","supports":["S001"]}]},
                   {"sectionType":"VERIFICATION","sentences":[{"text":"%s","supports":["S001"]}]},
                   {"sectionType":"STATUS","sentences":[{"text":"%s","supports":["S001"]}]}]}
                """.formatted(longText + "一", longText + "二", longText + "三",
                        longText + "四", longText + "五");
        assertRejected(overTotal);
    }

    private void assertRejected(String raw) {
        assertThatThrownBy(() -> codec.decode(raw, MaterialKind.FACT))
                .isInstanceOf(ExpressionDraftDecodingException.class);
    }
}
