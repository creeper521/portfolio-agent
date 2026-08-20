package com.portfolio.agent.infrastructure.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Loads the fixed, packaged system prompts once during application startup. */
public final class SystemPromptCatalog {
    static final String GOAL_INTERPRETATION_PATH =
            "prompts/goal-interpretation-system.txt";
    static final String GENERAL_KNOWLEDGE_PATH =
            "prompts/general-knowledge-system.txt";

    private final String goalInterpretation;
    private final String generalKnowledge;

    public SystemPromptCatalog() {
        this(SystemPromptCatalog.class.getClassLoader());
    }

    SystemPromptCatalog(ClassLoader classLoader) {
        ClassLoader requiredLoader = Objects.requireNonNull(classLoader, "classLoader");
        goalInterpretation = load(requiredLoader, GOAL_INTERPRETATION_PATH);
        generalKnowledge = load(requiredLoader, GENERAL_KNOWLEDGE_PATH);
    }

    public String goalInterpretation() {
        return goalInterpretation;
    }

    public String generalKnowledge() {
        return generalKnowledge;
    }

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
