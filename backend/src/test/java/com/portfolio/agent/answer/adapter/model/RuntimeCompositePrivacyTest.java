package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.dto.response.ConversationAnswerBlockResponse;
import com.portfolio.agent.answer.dto.response.PublicSourceReferenceResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeCompositePrivacyTest {

    @Test
    void p3BlockSerializationExposesPublishedReferencesAndAnchors() throws Exception {
        ConversationAnswerBlockResponse block = new ConversationAnswerBlockResponse(
                ConversationSourceScope.PORTFOLIO,
                "Bounded answer",
                List.of("published-claim"),
                List.of("published-evidence"),
                List.of(new PublicSourceReferenceResponse(
                        "REF-1", "Published source", "public-v1", "DOCUMENT",
                        "/projects/sql-audit", "/evidence/REF-1")));

        String json = new ObjectMapper().writeValueAsString(block);

        assertThat(json).contains("sourceReferences", "REF-1", "/projects/sql-audit")
                .contains("claimIds", "evidenceIds", "published-claim", "published-evidence");
    }
}
