package com.portfolio.agent.turn.lifecycle;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 请求指纹工厂：对闭合 Command 的规范字节串做 HMAC-SHA256 键控指纹。
 *
 * <p>指纹用于幂等 Claim 与冲突检测，替代持久化原始用户文本——访客问题只进入
 * HMAC 输入，从不落库。支持密钥轮换：当前密钥签名写入，历史密钥一并生成候选，
 * 与 {@link RequestFingerprintSet} 配套在轮换窗口内接受旧指纹。</p>
 */
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

    /** 用当前密钥计算单条指纹（测试便捷入口；生产路径使用 {@link #fingerprints}）。 */
    public byte[] fingerprint(AgentTurnCommand command) {
        return fingerprint(command, keys.getFirst().secret());
    }

    /**
     * 用当前密钥签名，并为全部历史密钥各生成一份候选指纹。
     *
     * @return 当前指纹位于首位、候选覆盖所有密钥世代的指纹集
     */
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

    /** 一个指纹密钥世代；secret 防御性复制，读取时同样返回副本。 */
    private record Key(String keyId, byte[] secret) {
        private Key {
            if (keyId == null || keyId.isBlank()) {
                throw new IllegalArgumentException("fingerprint key id is required");
            }
            secret = secret.clone();
        }
        @Override public byte[] secret() { return secret.clone(); }
    }

    /**
     * 把闭合 Command 序列化为规范字节串：每个字段以 UTF-8 长度前缀写入，分支用
     * 大写判别标记区分。字段顺序与判别标记是指纹兼容契约的一部分，任何调整都会
     * 使已存储指纹失效（表现为既有请求被判定为 CONFLICT）。
     */
    private byte[] canonical(AgentTurnCommand command) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            AgentTurnCommand.ModelSelection modelSelection = command.getModelSelection();
            value(output, modelSelection.getKind().name());
            value(output, modelSelection.getModelRef().orElse(""));
            value(output, modelSelection.getSelectionVersion().orElse(""));
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

    /** 长度前缀写入单个字符串字段，避免相邻字段拼接产生歧义编码。 */
    private void value(DataOutputStream output, String value) throws Exception {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
