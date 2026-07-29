package com.portfolio.agent.answer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.service.ProductionConversationService;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.RequestContextHolder;
import com.portfolio.agent.common.web.RequestDiagnosticsFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationAnswerControllerTest {

    private static final String TURN_ID = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";

    @Test
    void exposesV2ConversationContract() throws Exception {
        ProductionConversationService service = mock(ProductionConversationService.class);
        when(service.answer(any(), any())).thenAnswer(invocation -> {
            assertThat(RequestContextHolder.requireCurrent().getTurnId()).isEqualTo(TURN_ID);
            assertThat(MDC.get("turn.id")).isEqualTo(TURN_ID);
            return result();
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                service,
                                new ClientAddressResolver(false, java.util.Set.of()),
                                new ConversationAnswerResponseMapper()))
                .addFilters(new RequestDiagnosticsFilter(event -> { }))
                .build();
        String request = """
                {
	                  "turnId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
	                  "requestToken": "63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "question": "你好",
                  "messages": [],
                  "context": {
                    "audienceRole": "INTERVIEWER",
                    "source": "AGENT_PAGE"
                  }
                }
                """;

        mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CONVERSATION"))
                .andExpect(jsonPath("$.answerScope").value("CONVERSATION"))
                .andExpect(jsonPath("$.blocks[0].sourceScope").value("GENERAL"))
                .andExpect(jsonPath("$.degraded").value(false));

        verify(service).answer(any(), eq("127.0.0.1"));
    }

    @Test
    void missingRequestContextFailsBeforeCallingTheService() {
        RequestContextHolder.clear();
        ProductionConversationService service = mock(ProductionConversationService.class);
        ConversationAnswerController controller = new ConversationAnswerController(
                service,
                new ClientAddressResolver(false, java.util.Set.of()),
                new ConversationAnswerResponseMapper());
        ConversationAnswerRequest request = mock(ConversationAnswerRequest.class);

        assertThatThrownBy(() -> controller.answer(request, new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("request context is not available");
        verifyNoInteractions(service);
    }

    private ConversationAnswerResult result() {
        return new ConversationAnswerResult(
                TURN_ID,
                "v1",
                ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION,
                AnswerResolution.ANSWERED,
                "你好",
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL,
                        "你好，我可以聊通用技术，也可以介绍作品集。",
                        List.of(),
                        List.of())),
                List.of(),
                false);
    }
}
