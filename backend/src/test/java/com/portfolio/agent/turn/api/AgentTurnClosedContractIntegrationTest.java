package com.portfolio.agent.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortfolioAgentApplication.class, properties = {
        "portfolio.model-runtime.enabled=false",
        "portfolio.conversation-context.mode=IN_MEMORY"
})
@AutoConfigureMockMvc
class AgentTurnClosedContractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void freeTextDoesNotUseReviewedAliasAsAProviderFallback() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeText("63f63c75-16e8-49e7-864d-dcd0fe100d50",
                                "SQL 审计与故障排查工具")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.code").value("SEMANTIC_ROUTING_UNAVAILABLE"))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void providerUnavailableWithoutFallbackUsesStableSemanticRoutingCode() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeText("73f63c75-16e8-49e7-864d-dcd0fe100d50",
                                "请推荐两个项目")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.code").value("SEMANTIC_ROUTING_UNAVAILABLE"))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void lowInformationFreeTextDoesNotRequireProviderAvailability() throws Exception {
        mockMvc.perform(post("/api/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeText("75f63c75-16e8-49e7-864d-dcd0fe100d50", "112233")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CONVERSATIONAL"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void reviewedPresetUsesCurrentRevisionAndStaleRevisionFailsClosed() throws Exception {
        String publicContent = mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode preset = new ObjectMapper().readTree(publicContent)
                .path("questionPresets").get(0);
        String presetId = preset.path("id").asText();
        String revision = preset.path("contractVersion").asText();

        mockMvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content(preset("83f63c75-16e8-49e7-864d-dcd0fe100d50", presetId, revision)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content(preset("93f63c75-16e8-49e7-864d-dcd0fe100d50",
                                presetId, "pcv1-0000000000000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CAPABILITY_UNAVAILABLE"));
    }

    @Test
    void sameRequestIdWithDifferentCommandConflicts() throws Exception {
        String requestId = "a3f63c75-16e8-49e7-864d-dcd0fe100d50";
        mockMvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content(freeText(requestId, "SQL 审计与故障排查工具")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content(freeText(requestId, "活动系统工程实践")))
                .andExpect(status().isConflict());
    }

    @Test
    void oldOptionalFieldBagIsRejectedAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turnId":"old","requestToken":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                                 "question":"你好","messages":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String freeText(String requestId, String text) {
        return """
                {"requestId":"%s","modelSelection":{"kind":"NONE"},
                 "command":{"kind":"ASK","input":{
                 "kind":"FREE_TEXT","text":"%s"}},"conversationWindow":[]}
                """.formatted(requestId, text);
    }

    private String preset(String requestId, String presetId, String revision) {
        return """
                {"requestId":"%s","modelSelection":{"kind":"NONE"},
                 "command":{"kind":"ASK","input":{
                 "kind":"PRESET","presetId":"%s","presetRevision":"%s"}},
                 "conversationWindow":[]}
                """.formatted(requestId, presetId, revision);
    }
}
