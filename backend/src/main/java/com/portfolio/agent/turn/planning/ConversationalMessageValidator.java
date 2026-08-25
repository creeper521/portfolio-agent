package com.portfolio.agent.turn.planning;

import java.util.Locale;

/**
 * 对话消息校验器：对 CONVERSATIONAL 文案做封闭的输出约束校验。
 *
 * <p>约束包括长度上限、无控制字符、以中文为主体（拉丁字母数不超过汉字数
 * 两倍）、不含完整英文句子、不回放访客输入。用于防止 Provider 输出超范围
 * 或回显原文（隐私边界）。</p>
 */
public final class ConversationalMessageValidator {

    static final int MAX_CHARACTERS = 160;
    /** 参与回放检测的规范化输入最小长度，过短的输入不做回放判断。 */
    private static final int MIN_REPLAY_CHECK_CHARACTERS = 8;

    /**
     * 校验对话文案，通过则原样返回。
     *
     * @param userText 访客原始输入，用于回放检测
     * @throws IllegalArgumentException 文案为空/超长、含控制字符、中文占比
     *         不足、含完整英文句子或包含访客输入原文
     */
    public String validate(String message, String userText) {
        if (message == null || message.isBlank()
                || message.length() > MAX_CHARACTERS) {
            throw new IllegalArgumentException(
                    "conversational message is required and bounded");
        }
        if (message.chars().anyMatch(value -> Character.isISOControl(value))) {
            throw new IllegalArgumentException(
                    "conversational message contains control characters");
        }

        long hanCharacters = message.codePoints()
                .filter(value -> Character.UnicodeScript.of(value)
                        == Character.UnicodeScript.HAN)
                .count();
        long latinLetters = message.codePoints()
                .filter(value -> Character.UnicodeScript.of(value)
                        == Character.UnicodeScript.LATIN)
                .count();
        if (hanCharacters < 2 || latinLetters > hanCharacters * 2) {
            throw new IllegalArgumentException(
                    "conversational message must be primarily Chinese");
        }
        if (message.matches(".*[A-Za-z][A-Za-z ,'-]{15,}[.!?].*")) {
            throw new IllegalArgumentException(
                    "conversational message contains a complete English sentence");
        }

        String normalizedUserText = normalize(userText);
        String normalizedMessage = normalize(message);
        if (normalizedUserText.length() >= MIN_REPLAY_CHECK_CHARACTERS
                && normalizedMessage.contains(normalizedUserText)) {
            throw new IllegalArgumentException(
                    "conversational message repeats visitor input");
        }
        return message;
    }

    /** 规范化文本（去空白、转小写、压缩连续空白）用于回放比较。 */
    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
