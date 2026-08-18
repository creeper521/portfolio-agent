package com.portfolio.agent.answer.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.dto.response.AnswerBlockSupportResponse;
import com.portfolio.agent.answer.dto.response.ConversationAnswerBlockResponse;
import com.portfolio.agent.answer.dto.response.StatementSupportReferenceResponse;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerSupportContractTest {

    @Test
    void v2BlockCarriesOpaqueIdDomainAndAuthoritativeSupport() throws Exception {
        AnswerBlockSupportResponse support = new AnswerBlockSupportResponse(
                "VERIFIED_PUBLIC_EVIDENCE",
                List.of(new StatementSupportReferenceResponse(
                        "statement-01", List.of("source-01"), "public-v1")),
                List.of("source-01"), "public-v1");
        ConversationAnswerBlockResponse block = new ConversationAnswerBlockResponse(
                "block-01", TaskSourceDomain.PORTFOLIO, ConversationSourceScope.PORTFOLIO, null, null,
                "事实", List.of("claim-01"), List.of("evidence-01"), List.of(), support);

        String json = new ObjectMapper().writeValueAsString(block);

        assertThat(json).contains("\"blockId\":\"block-01\"")
                .contains("\"sourceDomain\":\"PORTFOLIO\"")
                .contains("\"support\"")
                .contains("VERIFIED_PUBLIC_EVIDENCE");
    }

    @Test
    void sourceCompositionAndCatalogAreAvailableAtTopLevel() throws Exception {
        assertThat(new com.portfolio.agent.answer.dto.response.PublicSourceCatalogEntryResponse(
                "source-01", "公开项目", "public-v1", "PROJECT", "/projects/a", "/evidence/e-1")
                .getReferenceKey()).isEqualTo("source-01");
        assertThat(new com.portfolio.agent.answer.dto.response.TaskSupportSummaryResponse(
                "VERIFIED_PUBLIC_EVIDENCE", 1, 1, 0, "public-v1")
                .getStatementCount()).isEqualTo(1);
    }
}
