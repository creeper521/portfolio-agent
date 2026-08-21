package com.portfolio.agent.common.web;

import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@AutoConfigureMockMvc
class RetiredVersionedApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/portfolio",
            "/api/v1/projects/sql-audit",
            "/api/v1/cases",
            "/api/v1/cases/multilingual-image-preservation",
            "/api/v1/public-content"
    })
    void retiredGetRoutesReturnJson404InsteadOfSpa(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void retiredDiagnosticsRouteReturnsJson404InsteadOfSpa() throws Exception {
        mockMvc.perform(post("/api/v1/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
