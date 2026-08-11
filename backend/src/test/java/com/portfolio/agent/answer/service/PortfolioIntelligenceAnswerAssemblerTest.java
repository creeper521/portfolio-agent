package com.portfolio.agent.answer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.request.ConversationMessageRequest;
import com.portfolio.agent.answer.intelligence.domain.AnswerFocus;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioIntelligenceAnswerAssemblerTest {

    private final DeterministicPortfolioAnswerComposer composer =
            new DeterministicPortfolioAnswerComposer();
    private final PortfolioIntelligenceAnswerAssembler assembler =
            new PortfolioIntelligenceAnswerAssembler(composer);

    @Test
    void singleSubjectFactLookupComposesTypedBlocksWithSummary() {
        PortfolioDecision decision = answeredDecision(AnswerFocus.overview());

        ConversationAnswerResult answer = assembler.assemble(request(), content(), decision);

        assertThat(answer.getSummary()).isEqualTo("公开项目摘要");
        assertThat(answer.getBlocks())
                .extracting(ConversationAnswerBlock::getSectionType)
                .containsExactly(
                        AnswerSectionType.BACKGROUND,
                        AnswerSectionType.SOLUTION);
        assertThat(answer.getBlocks()).allSatisfy(block -> {
            assertThat(block.getSourceScope()).isEqualTo(ConversationSourceScope.PORTFOLIO);
            assertThat(block.getTitle()).isNotBlank();
            assertThat(block.getContent()).isNotBlank();
            assertThat(block.getEvidenceIds()).isNotEmpty();
            assertThat(block.getClaimIds()).isNotEmpty();
        });
        assertThat(answer.getConstructionMode())
                .isEqualTo(AnswerConstructionMode.EVIDENCE_COMPOSITION);
        assertThat(answer.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(answer.getEvidenceState()).isEqualTo(AnswerEvidenceState.VERIFIED);
    }

    @Test
    void focusedFactLookupSkipsSummaryInBlocks() {
        PortfolioIntelligenceResult focused = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject("project-1")),
                List.of(passage("claim-ver", AnswerClaimCategory.VERIFICATION, "evidence-ver")),
                null, null, false, null)
                .withDecisionMetadata(AnswerIntentSource.RULE, false)
                .withAnswerFocus(AnswerFocus.focused(
                        List.of(AnswerClaimCategory.VERIFICATION)));
        PortfolioDecision decision = new PortfolioDecision(
                PortfolioDisposition.ANSWERED, focused);

        ConversationAnswerResult answer = assembler.assemble(request(), content(), decision);

        assertThat(answer.getSummary()).isNull();
        assertThat(answer.getBlocks())
                .extracting(ConversationAnswerBlock::getSectionType)
                .containsExactly(AnswerSectionType.VERIFICATION);
    }

    @Test
    void comparisonAndRecommendationStayOnTheLegacyPath() {
        PortfolioIntelligenceResult comparison = new PortfolioIntelligenceResult(
                PortfolioTaskMode.COMPARISON,
                List.of(subject("project-1")),
                List.of(passage("claim-1", AnswerClaimCategory.VERIFICATION, "evidence-1")),
                null, null, false, null)
                .withDecisionMetadata(AnswerIntentSource.RULE, false);
        PortfolioDecision comparisonDecision = new PortfolioDecision(
                PortfolioDisposition.ANSWERED, comparison);
        ConversationAnswerResult answer = assembler.assemble(
                request(), content(), comparisonDecision);

        assertThat(answer.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(answer.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getSectionType()).isNull();
            assertThat(block.getClaimIds()).containsExactly("claim-1");
        });

        com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation recommendation =
                new com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation(
                        "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        new com.portfolio.agent.answer.intelligence.domain
                                .PortfolioRecommendationContext(
                                "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                                "public-2026-07-31", "BACKEND", "INTERVIEWER",
                                Set.of("POSTGRESQL"), 2, List.of("project-1")),
                        List.of(), List.of(), List.of());
        PortfolioIntelligenceResult recommendationResult = new PortfolioIntelligenceResult(
                PortfolioTaskMode.RECOMMENDATION,
                List.of(subject("project-1")),
                List.of(passage("claim-1", AnswerClaimCategory.VERIFICATION, "evidence-1")),
                recommendation, null, false, null)
                .withDecisionMetadata(AnswerIntentSource.RULE, false);
        PortfolioDecision recommendationDecision = new PortfolioDecision(
                PortfolioDisposition.ANSWERED, recommendationResult);
        ConversationAnswerResult recommendationAnswer = assembler.assemble(
                request(), content(), recommendationDecision);

        assertThat(recommendationAnswer.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(recommendationAnswer.getBlocks()).singleElement().satisfies(block ->
                assertThat(block.getSectionType()).isNull());
    }

    @Test
    void clarificationAndContractFailuresKeepExistingResponsesOnTheDecisionPath() {
        PortfolioIntelligenceResult clarification = new PortfolioIntelligenceResult(
                PortfolioTaskMode.CLARIFICATION_REQUIRED,
                List.of(), List.of(), null,
                new com.portfolio.agent.answer.intelligence.domain.PortfolioClarification(
                        "请说明希望查询的内容。", "intent"),
                false, null)
                .withDecisionMetadata(AnswerIntentSource.RULE, false);
        PortfolioDecision clarificationDecision = new PortfolioDecision(
                PortfolioDisposition.NEEDS_CLARIFICATION, clarification);
        ConversationAnswerResult clarificationAnswer = assembler.assemble(
                request(), content(), clarificationDecision);

        assertThat(clarificationAnswer.getResolution())
                .isEqualTo(AnswerResolution.NEEDS_CLARIFICATION);
        assertThat(clarificationAnswer.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getSectionType()).isNull();
            assertThat(block.getContent()).isEqualTo("请说明希望查询的内容。");
        });

        PortfolioIntelligenceResult contractUnavailable = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP, List.of(), List.of(), null, null,
                "public-2026-07-31", false,
                com.portfolio.agent.answer.intelligence.service.ContractEvidenceSelector
                        .UNAVAILABLE_NOTICE)
                .withDecisionMetadata(AnswerIntentSource.PRESET, false);
        PortfolioDecision contractDecision = new PortfolioDecision(
                PortfolioDisposition.CAPABILITY_UNAVAILABLE, contractUnavailable);
        ConversationAnswerResult contractAnswer = assembler.assemble(
                request(), content(), contractDecision);

        assertThat(contractAnswer.getResolution())
                .isEqualTo(AnswerResolution.CAPABILITY_UNAVAILABLE);
        assertThat(contractAnswer.getBlocks()).singleElement().satisfies(block ->
                assertThat(block.getContent())
                        .isEqualTo("这个推荐问题暂时无法回答，内容正在更新。"));
    }

    @Test
    void notSupportedDoesNotLeakUnrelatedEvidence() {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject("project-1")),
                List.of(passage("unrelated", AnswerClaimCategory.BACKGROUND, "evidence-bg")),
                null, null, false, null)
                .withDecisionMetadata(AnswerIntentSource.RULE, false)
                .withAnswerFocus(AnswerFocus.focused(List.of(AnswerClaimCategory.VERIFICATION)));

        ConversationAnswerResult answer = assembler.assemble(
                request(), content(), new PortfolioDecision(
                        PortfolioDisposition.NOT_SUPPORTED, result));

        assertThat(answer.getResolution()).isEqualTo(AnswerResolution.NOT_SUPPORTED);
        assertThat(answer.getEvidenceState()).isEqualTo(AnswerEvidenceState.INSUFFICIENT);
        assertThat(answer.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getContent()).isEqualTo("当前公开内容中没有足够的已验证材料。");
            assertThat(block.getClaimIds()).isEmpty();
            assertThat(block.getEvidenceIds()).isEmpty();
        });
    }

    @Test
    void successfulCompositionPreservesDegradedMetadata() {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject("project-1")),
                List.of(passage("claim-bg", AnswerClaimCategory.BACKGROUND, "evidence-bg")),
                null, null, "public-1", true, "POSTGRES_FALLBACK")
                .withDecisionMetadata(AnswerIntentSource.RULE, false);

        ConversationAnswerResult answer = assembler.assemble(
                request(), content(), new PortfolioDecision(
                        PortfolioDisposition.ANSWERED, result));

        assertThat(answer.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(answer.isDegraded()).isTrue();
        assertThat(answer.getNoticeCode()).isEqualTo("POSTGRES_FALLBACK");
        assertThat(answer.getBlocks()).isNotEmpty();
    }

    @Test
    void composerFailureFailsClosedWithSafeTemplate() {
        PortfolioIntelligenceResult invalid = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject("project-1"), subject("project-2")),
                List.of(passage("claim-1", AnswerClaimCategory.VERIFICATION, "evidence-1")),
                null, null, false, null)
                .withDecisionMetadata(AnswerIntentSource.RULE, false);
        PortfolioDecision decision = new PortfolioDecision(
                PortfolioDisposition.ANSWERED, invalid);

        ConversationAnswerResult answer = assembler.assemble(request(), content(), decision);

        assertThat(answer.getResolution()).isEqualTo(AnswerResolution.CAPABILITY_UNAVAILABLE);
        assertThat(answer.getEvidenceState()).isEqualTo(AnswerEvidenceState.INSUFFICIENT);
        assertThat(answer.getConstructionMode()).isEqualTo(AnswerConstructionMode.TEMPLATE);
        assertThat(answer.isDegraded()).isTrue();
        assertThat(answer.getNoticeCode()).isEqualTo("ANSWER_COMPOSITION_INVALID");
        assertThat(answer.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getContent()).isEqualTo("当前公开材料暂时无法形成可靠回答。");
            assertThat(block.getClaimIds()).isEmpty();
            assertThat(block.getEvidenceIds()).isEmpty();
        });
    }

    private PortfolioDecision answeredDecision(AnswerFocus focus) {
        PortfolioIntelligenceResult result = new PortfolioIntelligenceResult(
                PortfolioTaskMode.FACT_LOOKUP,
                List.of(subject("project-1")),
                List.of(
                        passage("claim-bg", AnswerClaimCategory.BACKGROUND, "evidence-bg"),
                        passage("claim-sol", AnswerClaimCategory.IMPLEMENTATION, "evidence-sol")),
                null, null, false, null)
                .withDecisionMetadata(AnswerIntentSource.RULE, false)
                .withAnswerFocus(focus);
        return new PortfolioDecision(PortfolioDisposition.ANSWERED, result);
    }

    private PortfolioRetrievedSubject subject(String id) {
        return new PortfolioRetrievedSubject(
                id, "PROJECT", "SQL 审计与故障排查工具", "公开项目摘要", "/projects/" + id,
                "BACKEND", Set.of("POSTGRESQL"), 1.0d, 1.0d, 0.0d);
    }

    private PortfolioRetrievedPassage passage(
            String claimId,
            AnswerClaimCategory category,
            String evidenceId) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId,
                category,
                "已验证主要功能流程。",
                "验证范围以公开证据为限。",
                AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("POSTGRESQL"),
                List.of(evidenceId));
        return new PortfolioRetrievedPassage(
                "project-1#" + claimId,
                "project-1",
                "已验证主要功能流程。",
                claim,
                List.of(new PortfolioRetrievedEvidenceReference(
                        evidenceId, "公开证据", "APPROVED")));
    }

    private ConversationAnswerRequest request() {
        return new ConversationAnswerRequest(
                "turn-1",
                UUID.nameUUIDFromBytes("turn-1".getBytes()),
                "介绍 SQL 审计项目",
                List.of(new ConversationMessageRequest(
                        com.portfolio.agent.answer.domain.ConversationMessageRole.USER,
                        "介绍 SQL 审计项目")),
                new ConversationAnswerContextRequest(
                        null, null, null,
                        com.portfolio.agent.answer.dto.request.AnswerRequestSource.HOME));
    }

    private RuntimeAnswerContent content() {
        return new RuntimeAnswerContent("public-2026-07-31", "hash", List.of());
    }
}
