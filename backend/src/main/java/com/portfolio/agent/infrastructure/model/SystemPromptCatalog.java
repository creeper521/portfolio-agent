package com.portfolio.agent.infrastructure.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 系统提示词目录：在应用启动时一次性加载打包内的固定系统提示词。
 *
 * <p>两份提示词（目标解释、通用知识）作为只读资源随 JAR 发布，运行期不变化、
 * 不外取。加载过程 fail-closed：资源缺失、不可读、非严格 UTF-8 或内容为空
 * 都会抛出带分类标记的 IllegalStateException，阻止应用带病启动。
 */
public final class SystemPromptCatalog {
    static final String GOAL_INTERPRETATION_PATH =
            "prompts/goal-interpretation-system.txt";
    static final String GENERAL_KNOWLEDGE_PATH =
            "prompts/general-knowledge-system.txt";
    static final String GENERAL_PROVIDER_DRAFT_PATH =
            "prompts/general-provider-draft-system.txt";

    private final String goalInterpretation;
    private final String generalKnowledge;
    private final String generalProviderDraft;

    public SystemPromptCatalog() {
        this(SystemPromptCatalog.class.getClassLoader());
    }

    /** 以指定类加载器加载目录（测试可注入自定义加载器）。 */
    SystemPromptCatalog(ClassLoader classLoader) {
        ClassLoader requiredLoader = Objects.requireNonNull(classLoader, "classLoader");
        goalInterpretation = load(requiredLoader, GOAL_INTERPRETATION_PATH);
        generalKnowledge = load(requiredLoader, GENERAL_KNOWLEDGE_PATH);
        generalProviderDraft = load(requiredLoader, GENERAL_PROVIDER_DRAFT_PATH);
    }

    public String goalInterpretation() {
        return goalInterpretation;
    }

    public String generalKnowledge() {
        return generalKnowledge;
    }

    public String generalProviderDraft() {
        return generalProviderDraft;
    }

    /**
     * 从类路径读取并严格校验一份提示词：必须存在、可读、为合法 UTF-8
     * 且去空白后非空，任何失败都转为启动期异常。
     */
    private String load(ClassLoader classLoader, String path) {
        byte[] bytes;
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw failure("MISSING", path);
            }
            bytes = input.readAllBytes();
        } catch (IOException failure) {
            throw failure("UNREADABLE", path);
        }

        String prompt;
        try {
            prompt = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString().trim();
        } catch (CharacterCodingException failure) {
            throw failure("INVALID_UTF8", path);
        }
        if (prompt.isBlank()) {
            throw failure("EMPTY", path);
        }
        return prompt;
    }

    private IllegalStateException failure(String category, String path) {
        return new IllegalStateException("SYSTEM_PROMPT_" + category + ": " + path);
    }
}
