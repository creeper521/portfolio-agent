package com.portfolio.agent.turn.api.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTurnRequestMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentTurnRequestMapper requestMapper = new AgentTurnRequestMapper();

    @Test
    void mapsFreeTextAskWithoutLegacyActionFields() throws Exception {
        AgentTurnRequest request = mapper.readValue("""
                {
                  "requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50",
                  "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"介绍这个项目"}},
                  "surfaceContext":{
                    "subjectHint":{"kind":"PROJECT","slug":"sql-audit"},
                    "audienceRole":"INTERVIEWER",
                    "requestSource":"AGENT_PAGE"
                  },
                  "conversationWindow":[{"role":"USER","content":"上下文"}]
                }
                """, AgentTurnRequest.class);

        AgentTurnCommand command = requestMapper.toCommand(request);

        assertThat(command).isInstanceOf(AgentTurnCommand.Ask.class);
        AgentTurnCommand.Ask ask = (AgentTurnCommand.Ask) command;
        assertThat(ask.getInput()).isInstanceOf(AgentTurnCommand.FreeText.class);
        assertThat(((AgentTurnCommand.FreeText) ask.getInput()).getText()).isEqualTo("介绍这个项目");
        assertThat(command.getRequestId().toString())
                .isEqualTo("63f63c75-16e8-49e7-864d-dcd0fe100d50");
        assertThat(command.getSurfaceContext().getSubjectHint().getSlug()).isEqualTo("sql-audit");
        assertThat(command.getConversationWindow().getMessages()).hasSize(1);
    }

    @Test
    void mapsPresetContinueAndClarificationToMutuallyExclusiveCommands() throws Exception {
        AgentTurnCommand preset = requestMapper.toCommand(read("""
                {"kind":"ASK","input":{"kind":"PRESET","presetId":"question-sql-audit-detail",
                 "presetRevision":"pcv1-0123456789abcdef"}}
                """));
        AgentTurnCommand continuation = requestMapper.toCommand(read("""
                {"kind":"CONTINUE","contextHandle":"ctx_opaque","resultItemId":"item_opaque",
                 "text":"继续"}
                """));
        AgentTurnCommand clarification = requestMapper.toCommand(read("""
                {"kind":"RESOLVE_CLARIFICATION","clarificationId":"clarification_opaque",
                 "answer":{"kind":"CHOICE","choiceId":"choice_opaque"}}
                """));

        assertThat(((AgentTurnCommand.Ask) preset).getInput())
                .isInstanceOf(AgentTurnCommand.Preset.class);
        assertThat(continuation).isInstanceOf(AgentTurnCommand.Continue.class);
        assertThat(((AgentTurnCommand.Continue) continuation).getResultItemId())
                .contains("item_opaque");
        assertThat(clarification).isInstanceOf(AgentTurnCommand.ResolveClarification.class);
        assertThat(((AgentTurnCommand.ResolveClarification) clarification).getAnswer())
                .isInstanceOf(AgentTurnCommand.ChoiceAnswer.class);
    }

    private AgentTurnRequest read(String command) throws Exception {
        return mapper.readValue("""
                {"requestId":"63f63c75-16e8-49e7-864d-dcd0fe100d50","command":%s,
                 "conversationWindow":[]}
                """.formatted(command), AgentTurnRequest.class);
    }
}
