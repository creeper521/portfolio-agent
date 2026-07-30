package com.portfolio.agent.selection.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.agent.common.web.GlobalExceptionHandler;
import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.gateway.CandidateRetrievalPort;
import com.portfolio.agent.selection.mapper.PortfolioSelectionResponseMapper;
import com.portfolio.agent.selection.service.ExhaustiveSelectionStrategy;
import com.portfolio.agent.selection.service.PortfolioSelectionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PortfolioSelectionControllerTest {

    @Test
    void returnsFullReadyJsonShapeWithApprovedEvidence() throws Exception {
        MockMvc mockMvc = mockMvc(successfulPort(RetrievalMode.HYBRID, 4));

        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectionId").value(org.hamcrest.Matchers.startsWith("sel_")))
                .andExpect(jsonPath("$.releaseVersion").value("2026-07-30.1"))
                .andExpect(jsonPath("$.policyVersion").value("exhaustive-v1"))
                .andExpect(jsonPath("$.retrievalMode").value("HYBRID"))
                .andExpect(jsonPath("$.selectionMode").value("EXHAUSTIVE"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.requestedSize").value(3))
                .andExpect(jsonPath("$.actualSize").value(3))
                .andExpect(jsonPath("$.coverage").isArray())
                .andExpect(jsonPath("$.coverage[0].capabilityCode").isNotEmpty())
                .andExpect(jsonPath("$.coverage[0].label").isNotEmpty())
                .andExpect(jsonPath("$.coverage[0].coveredBySubjectIds").isArray())
                .andExpect(jsonPath("$.items[0].title").isNotEmpty())
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty())
                .andExpect(jsonPath("$.items[0].route").isNotEmpty())
                .andExpect(jsonPath("$.items[0].selectionReason").isNotEmpty())
                .andExpect(jsonPath("$.items[0].evidenceRefs[0].claimId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].evidenceRefs[0].evidenceId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].evidenceRefs[0].label").isNotEmpty())
                .andExpect(jsonPath("$.complementarity").isArray())
                .andExpect(jsonPath("$.complementarity[0].leftSubjectId").isNotEmpty())
                .andExpect(jsonPath("$.complementarity[0].rightSubjectId").isNotEmpty())
                .andExpect(jsonPath("$.complementarity[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.alternatives[0].subjectId").isNotEmpty())
                .andExpect(jsonPath("$.alternatives[0].subjectType").isNotEmpty())
                .andExpect(jsonPath("$.alternatives[0].title").isNotEmpty())
                .andExpect(jsonPath("$.alternatives[0].summary").isNotEmpty())
                .andExpect(jsonPath("$.alternatives[0].route").isNotEmpty())
                .andExpect(jsonPath("$.alternatives[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.degradation").doesNotExist());
    }

    @Test
    void defaultsRequestedSizeToThreeAndAcceptsBothBounds() throws Exception {
        MockMvc mockMvc = mockMvc(successfulPort(RetrievalMode.HYBRID, 5));

        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedSize").value(3))
                .andExpect(jsonPath("$.actualSize").value(3));
        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualSize").value(2));
        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualSize").value(5));
    }

    @Test
    void rejectsInvalidSizeAndUnknownAudienceRoleWithGlobalBadRequestShape() throws Exception {
        MockMvc mockMvc = mockMvc(successfulPort(RetrievalMode.HYBRID, 5));

        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"audienceRole":"UNKNOWN_ROLE","requestedSize":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void exposesFtsOnlyDegradationWithoutPretendingHybridRetrieval() throws Exception {
        MockMvc mockMvc = mockMvc(successfulPort(RetrievalMode.FTS_ONLY, 3));

        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievalMode").value("FTS_ONLY"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.degradation.code").value("VECTOR_RETRIEVAL_UNAVAILABLE"));
    }

    @Test
    void returnsInsufficientWithoutPadding() throws Exception {
        MockMvc mockMvc = mockMvc(successfulPort(RetrievalMode.HYBRID, 1));

        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.actualSize").value(1))
                .andExpect(jsonPath("$.degradation.code").value("INSUFFICIENT_ELIGIBLE_ASSETS"));
    }

    @Test
    void returnsStableTemporarilyUnavailableEnvelopeWhenRetrievalFails() throws Exception {
        MockMvc mockMvc = mockMvc((target, limit) -> {
            throw new com.portfolio.agent.selection.gateway.CandidateRetrievalException(
                    "database unavailable");
        });

        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.retrievalMode").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.actualSize").value(0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.degradation.code").value("PUBLIC_SELECTION_UNAVAILABLE"));
    }

    private MockMvc mockMvc(CandidateRetrievalPort retrievalPort) {
        PortfolioSelectionController controller = new PortfolioSelectionController(
                new PortfolioSelectionService(retrievalPort, new ExhaustiveSelectionStrategy()),
                new PortfolioSelectionResponseMapper());
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CandidateRetrievalPort successfulPort(RetrievalMode mode, int count) {
        return (target, limit) -> new CandidateRetrievalResult(
                "2026-07-30.1",
                mode,
                candidates(count));
    }

    private List<SelectionCandidate> candidates(int count) {
        List<SelectionCandidate> candidates = new ArrayList<>();
        List<String> capabilities = List.of(
                "JAVA", "INCIDENT_ANALYSIS", "AGENT", "DELIVERY", "RAG");
        for (int index = 0; index < count; index++) {
            String id = index % 2 == 0
                    ? "PROJECT-" + (index + 1)
                    : "CASE-" + (index + 1);
            PortfolioSubjectKind kind = id.startsWith("PROJECT")
                    ? PortfolioSubjectKind.PROJECT
                    : PortfolioSubjectKind.CASE;
            candidates.add(new SelectionCandidate(
                    id,
                    kind,
                    "Title " + id,
                    "Summary " + id,
                    (kind == PortfolioSubjectKind.PROJECT ? "/projects/" : "/cases/")
                            + id.toLowerCase(),
                    "JAVA_BACKEND",
                    Set.of(capabilities.get(index)),
                    List.of(new EvidenceReference(
                            "CLAIM-" + index,
                            "EVIDENCE-" + index,
                            "Approved evidence " + index)),
                    1.0 - index * 0.05,
                    1.0,
                    0.0));
        }
        return List.copyOf(candidates);
    }

    private String request(Integer requestedSize) {
        String size = requestedSize == null ? "" : ",\"requestedSize\":" + requestedSize;
        return """
                {
                  "careerTrack": "JAVA_BACKEND",
                  "audienceRole": "INTERVIEWER",
                  "capabilityCodes": []
                %s
                }
                """.formatted(size);
    }
}
