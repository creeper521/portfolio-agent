package com.portfolio.agent.turn.api.request;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public final class AgentTurnRequestMapper {

    public AgentTurnCommand toCommand(AgentTurnRequest request) {
        Objects.requireNonNull(request, "request");
        AgentTurnCommand.SurfaceContext surfaceContext = mapSurfaceContext(request.getSurfaceContext());
        ConversationWindow conversationWindow = mapConversationWindow(request.getConversationWindow());
        AgentTurnRequest.CommandRequest command = Objects.requireNonNull(
                request.getCommand(), "request.command");
        if (command instanceof AgentTurnRequest.AskCommandRequest ask) {
            return new AgentTurnCommand.Ask(
                    request.getRequestId(), mapAskInput(ask.getInput()),
                    surfaceContext, conversationWindow);
        }
        if (command instanceof AgentTurnRequest.ContinueCommandRequest continuation) {
            return new AgentTurnCommand.Continue(
                    request.getRequestId(), continuation.getContextHandle(),
                    continuation.getResultItemId(), continuation.getText(),
                    surfaceContext, conversationWindow);
        }
        if (command instanceof AgentTurnRequest.ResolveClarificationCommandRequest clarification) {
            return new AgentTurnCommand.ResolveClarification(
                    request.getRequestId(), clarification.getClarificationId(),
                    mapClarificationAnswer(clarification.getAnswer()),
                    surfaceContext, conversationWindow);
        }
        throw new IllegalArgumentException("unsupported command request");
    }

    private AgentTurnCommand.AskInput mapAskInput(AgentTurnRequest.AskInputRequest input) {
        if (input instanceof AgentTurnRequest.FreeTextInputRequest freeText) {
            return new AgentTurnCommand.FreeText(freeText.getText());
        }
        if (input instanceof AgentTurnRequest.PresetInputRequest preset) {
            return new AgentTurnCommand.Preset(preset.getPresetId(), preset.getPresetRevision());
        }
        throw new IllegalArgumentException("unsupported ask input");
    }

    private AgentTurnCommand.ClarificationAnswer mapClarificationAnswer(
            AgentTurnRequest.ClarificationAnswerRequest answer) {
        if (answer instanceof AgentTurnRequest.ChoiceAnswerRequest choice) {
            return new AgentTurnCommand.ChoiceAnswer(choice.getChoiceId());
        }
        if (answer instanceof AgentTurnRequest.TextAnswerRequest text) {
            return new AgentTurnCommand.TextAnswer(text.getText());
        }
        throw new IllegalArgumentException("unsupported clarification answer");
    }

    private AgentTurnCommand.SurfaceContext mapSurfaceContext(
            AgentTurnRequest.SurfaceContextRequest request) {
        if (request == null) {
            return AgentTurnCommand.SurfaceContext.empty();
        }
        AgentTurnCommand.SubjectHint subjectHint = request.getSubjectHint() == null
                ? null
                : new AgentTurnCommand.SubjectHint(
                        AgentTurnCommand.SubjectHintKind.valueOf(
                                request.getSubjectHint().getKind().name()),
                        request.getSubjectHint().getSlug());
        AgentTurnCommand.AudienceRole audienceRole = request.getAudienceRole() == null
                ? null
                : AgentTurnCommand.AudienceRole.valueOf(request.getAudienceRole().name());
        AgentTurnCommand.RequestSource requestSource = request.getRequestSource() == null
                ? null
                : AgentTurnCommand.RequestSource.valueOf(request.getRequestSource().name());
        return new AgentTurnCommand.SurfaceContext(subjectHint, audienceRole, requestSource);
    }

    private ConversationWindow mapConversationWindow(List<AgentTurnRequest.MessageRequest> requests) {
        List<ConversationWindow.Message> messages = new ArrayList<>();
        for (AgentTurnRequest.MessageRequest request : requests) {
            messages.add(new ConversationWindow.Message(
                    ConversationWindow.Role.valueOf(request.getRole().name()),
                    request.getText()));
        }
        return new ConversationWindow(List.copyOf(messages));
    }
}
