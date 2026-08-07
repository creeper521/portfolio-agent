package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loader for generation rule documents (e.g.
 * generation-rules/public-subject-smoke.v1.json). Unknown fields are rejected.
 */
public final class GenerationRuleLoader {

    private final ObjectMapper mapper;

    public GenerationRuleLoader() {
        this.mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public GenerationRule load(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            return mapper.readValue(Files.readAllBytes(path), GenerationRule.class);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid generation rule", failure);
        }
    }

    public static final class GenerationRule {
        private final String schemaVersion;
        private final String ruleId;
        private final String selector;
        private final List<String> subjectTypes;
        private final String template;

        @JsonCreator
        public GenerationRule(
                @JsonProperty("schemaVersion") String schemaVersion,
                @JsonProperty("ruleId") String ruleId,
                @JsonProperty("selector") String selector,
                @JsonProperty("subjectTypes") List<String> subjectTypes,
                @JsonProperty("template") String template) {
            this.schemaVersion = schemaVersion;
            this.ruleId = ruleId;
            this.selector = selector;
            this.subjectTypes = subjectTypes == null ? List.of() : List.copyOf(subjectTypes);
            this.template = template;
        }

        public String getSchemaVersion() { return schemaVersion; }
        public String getRuleId() { return ruleId; }
        public String getSelector() { return selector; }
        public List<String> getSubjectTypes() { return subjectTypes; }
        public String getTemplate() { return template; }
    }
}
