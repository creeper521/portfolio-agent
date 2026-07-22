package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.FollowUpIntent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEnvelopeResponseTest {

    @Test
    void exposesOnlyDefensivelyCopiedStableReferences() {
        List<String> projectSlugs = new ArrayList<>(List.of("sql-audit"));
        List<String> claimIds = new ArrayList<>(List.of("claim-sql-audit-delivered"));

        ContextEnvelopeResponse response = new ContextEnvelopeResponse(
                "2026-07-21.1",
                projectSlugs,
                "sql-audit-overview",
                claimIds,
                AnswerSectionType.STATUS,
                FollowUpIntent.CURRENT_STATUS);
        projectSlugs.clear();
        claimIds.clear();

        assertThat(response.getProjectSlugs()).containsExactly("sql-audit");
        assertThat(response.getReferencedClaimIds())
                .containsExactly("claim-sql-audit-delivered");
        assertThat(response.toString())
                .contains("CURRENT_STATUS", "projectCount=1", "claimCount=1")
                .doesNotContain("sql-audit", "claim-sql-audit-delivered");
    }
}
