package com.portfolio.agent.turn.lifecycle;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Keyed fingerprint of the canonical closed command; raw user text is never persisted. */
public final class RequestFingerprintFactory {
    private final byte[] secret;
    public RequestFingerprintFactory(byte[] secret) {
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        if (this.secret.length < 32) throw new IllegalArgumentException("fingerprint secret is too short");
    }

    public byte[] fingerprint(AgentTurnCommand command) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(canonical(command));
        } catch (Exception exception) {
            throw new IllegalStateException("request fingerprint unavailable", exception);
        }
    }

    private byte[] canonical(AgentTurnCommand command) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            if (command instanceof AgentTurnCommand.Ask ask) {
                value(output, "ASK");
                if (ask.getInput() instanceof AgentTurnCommand.FreeText freeText) {
                    value(output, "FREE_TEXT"); value(output, freeText.getText());
                } else if (ask.getInput() instanceof AgentTurnCommand.Preset preset) {
                    value(output, "PRESET"); value(output, preset.getPresetId());
                    value(output, preset.getPresetRevision());
                } else throw new IllegalArgumentException("unknown ask input");
            } else if (command instanceof AgentTurnCommand.Continue continuation) {
                value(output, "CONTINUE"); value(output, continuation.getContextHandle());
                value(output, continuation.getResultItemId().orElse(""));
                value(output, continuation.getText());
            } else if (command instanceof AgentTurnCommand.ResolveClarification clarification) {
                value(output, "RESOLVE_CLARIFICATION"); value(output, clarification.getClarificationId());
                if (clarification.getAnswer() instanceof AgentTurnCommand.ChoiceAnswer choice) {
                    value(output, "CHOICE"); value(output, choice.getChoiceId());
                } else if (clarification.getAnswer() instanceof AgentTurnCommand.TextAnswer text) {
                    value(output, "TEXT"); value(output, text.getText());
                } else throw new IllegalArgumentException("unknown clarification answer");
            } else throw new IllegalArgumentException("unknown command");
            AgentTurnCommand.SurfaceContext surface = command.getSurfaceContext();
            if (surface.getSubjectHint() == null) {
                value(output, "NO_SUBJECT_HINT");
            } else {
                value(output, surface.getSubjectHint().getKind().name());
                value(output, surface.getSubjectHint().getSlug());
            }
            value(output, surface.getAudienceRole().map(Enum::name).orElse(""));
            value(output, surface.getRequestSource().map(Enum::name).orElse(""));
            output.writeInt(command.getConversationWindow().getMessages().size());
            for (ConversationWindow.Message message : command.getConversationWindow().getMessages()) {
                value(output, message.getRole().name());
                value(output, message.getText());
            }
        }
        return bytes.toByteArray();
    }

    private void value(DataOutputStream output, String value) throws Exception {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
