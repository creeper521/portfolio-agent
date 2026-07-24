package com.portfolio.agent.answer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.service.ConversationalAgentRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationAnswerControllerTest {

    @Test
    void exposesV2ConversationContract() throws Exception {
        ConversationalAgentRuntime runtime = mock(ConversationalAgentRuntime.class);
        when(runtime.answer(any())).thenReturn(result());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ConversationAnswerController(
                                runtime,
                                new ConversationAnswerResponseMapper()))
                .build();
        String request = """
                {
                  "turnId": "turn-1",
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
    }

    private ConversationAnswerResult result() {
        return new ConversationAnswerResult(
                "turn-1",
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
