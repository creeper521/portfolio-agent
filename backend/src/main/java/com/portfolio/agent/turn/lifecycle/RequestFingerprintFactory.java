package com.portfolio.agent.turn.lifecycle;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Keyed fingerprint of the canonical closed command; raw user text is never persisted. */
public final class RequestFingerprintFactory {
    private final List<Key> keys;
    public RequestFingerprintFactory(byte[] secret) {
        this("test-current", secret, java.util.Map.of());
    }
    public RequestFingerprintFactory(byte[] currentSecret, List<byte[]> previousSecrets) {
        this("test-current", currentSecret, indexed(previousSecrets));
    }
    public RequestFingerprintFactory(
            String currentKeyId, byte[] currentSecret,
            java.util.Map<String, byte[]> previousSecrets) {
        ArrayList<Key> configured = new ArrayList<>();
        configured.add(new Key(currentKeyId, requireSecret(currentSecret)));
        Objects.requireNonNull(previousSecrets, "previousSecrets")
                .forEach((keyId, value) -> configured.add(
                        new Key(keyId, requireSecret(value))));
        keys = List.copyOf(configured);
    }

    public byte[] fingerprint(AgentTurnCommand command) {
        return fingerprint(command, keys.getFirst().secret());
    }

    public RequestFingerprintSet fingerprints(AgentTurnCommand command) {
        List<RequestFingerprintSet.Candidate> values = keys.stream()
                .map(key -> new RequestFingerprintSet.Candidate(
                        key.keyId(), fingerprint(command, key.secret()))).toList();
        return new RequestFingerprintSet(
                values.getFirst().keyId(), values.getFirst().fingerprint(), values);
    }

    private byte[] fingerprint(AgentTurnCommand command, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(canonical(command));
        } catch (Exception exception) {
            throw new IllegalStateException("request fingerprint unavailable", exception);
        }
    }

    private byte[] requireSecret(byte[] value) {
        byte[] copy = Objects.requireNonNull(value, "secret").clone();
        if (copy.length < 32) throw new IllegalArgumentException("fingerprint secret is too short");
        return copy;
    }

    private static java.util.Map<String, byte[]> indexed(List<byte[]> values) {
        java.util.LinkedHashMap<String, byte[]> indexed = new java.util.LinkedHashMap<>();
        int index = 0;
        for (byte[] value : values) indexed.put("test-previous-" + index++, value);
        return java.util.Map.copyOf(indexed);
    }

    private record Key(String keyId, byte[] secret) {
        private Key {
            if (keyId == null || keyId.isBlank()) {
                throw new IllegalArgumentException("fingerprint key id is required");
            }
            secret = secret.clone();
        }
        @Override public byte[] secret() { return secret.clone(); }
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
                value(output, ask.getReferenceContextHandle().orElse(""));
            } else if (command instanceof AgentTurnCommand.Continue continuation) {
                value(output, "CONTINUE"); value(output, continuation.getOperation().name());
                value(output, continuation.getContextHandle().orElse(""));
                value(output, continuation.getResultItemId().orElse(""));
                value(output, continuation.getText().orElse(""));
                if (continuation.getSubject().isPresent()) {
                    AgentTurnCommand.ContinueSubject subject =
                            continuation.getSubject().orElseThrow();
                    value(output, subject.getKind().name());
                    value(output, subject.getReference());
                } else {
                    value(output, "");
                    value(output, "");
                }
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
