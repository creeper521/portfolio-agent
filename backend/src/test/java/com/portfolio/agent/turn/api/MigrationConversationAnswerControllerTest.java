package com.portfolio.agent.turn.api;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.RequestDiagnosticsFilter;
import com.portfolio.agent.turn.api.request.AgentTurnRequestMapper;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.MigrationProductionTurnService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MigrationConversationAnswerControllerTest {

    @Test
    void acceptsOnlyClosedRequestAndPassesCommandToService() throws Exception {
        MigrationProductionTurnService service = mock(MigrationProductionTurnService.class);
        when(service.answer(any(), eq("127.0.0.1"))).thenReturn(new ConversationAnswerResult(
                "63f63c75-16e8-49e7-864d-dcd0fe100d50", "2026-08-05.1",
                ConversationIntent.CONVERSATION, ConversationAnswerScope.CONVERSATION,
                AnswerResolution.ANSWERED, "你好", List.of(), List.of(), false));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MigrationConversationAnswerController(
                        service, new AgentTurnRequestMapper(), new ConversationAnswerResponseMapper(),
                        new ClientAddressResolver(false, java.util.Set.of())))
                .setValidator(validator)
                .addFilters(new RequestDiagnosticsFilter(event -> { }))
                .build();

        mvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                                 "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}},
                                 "conversationWindow":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.resolution").value("ANSWERED"));

        verify(service).answer(any(AgentTurnCommand.class), eq("127.0.0.1"));
    }
}
