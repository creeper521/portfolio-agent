package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EvalManifestLoader {

    private final ObjectMapper mapper;

    public EvalManifestLoader() {
        this.mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public EvalManifest load(Path manifestPath) {
        Objects.requireNonNull(manifestPath, "manifestPath");
        ManifestDocument document;
        try {
            document = mapper.readValue(
                    Files.readAllBytes(manifestPath), ManifestDocument.class);
        } catch (IOException failure) {
            throw new IllegalArgumentException(
                    "Invalid evaluation manifest", failure);
        }
        Path root = manifestPath.toAbsolutePath().getParent().normalize();
        List<Path> tracked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String file : document.trackedCaseFiles) {
            if (!seen.add(file)) {
                throw new IllegalArgumentException(
                        "Duplicate tracked case file: " + file);
            }
            Path resolved = root.resolve(file).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException(
                        "Tracked case file escapes manifest root: " + file);
            }
            if (!Files.exists(resolved)) {
                throw new IllegalArgumentException(
                        "Missing tracked case file: " + file);
            }
            tracked.add(resolved);
        }
        if (document.challenge != null
                && document.challenge.pathStoredInRepository) {
            throw new IllegalArgumentException(
                    "Challenge data must not be stored in the repository");
        }
        List<Path> generationRules = new ArrayList<>();
        if (document.generationRuleFiles != null) {
            for (String file : document.generationRuleFiles) {
                Path resolved = root.resolve(file).normalize();
                if (!resolved.startsWith(root) || !Files.exists(resolved)) {
                    throw new IllegalArgumentException(
                            "Invalid generation rule file: " + file);
                }
                generationRules.add(resolved);
            }
        }
        return new EvalManifest(
                document.schemaVersion,
                document.suiteId,
                document.datasetVersion,
                List.copyOf(tracked),
                List.copyOf(generationRules));
    }

    public static final class EvalManifest {
        private final String schemaVersion;
        private final String suiteId;
        private final String datasetVersion;
        private final List<Path> trackedCaseFiles;
        private final List<Path> generationRuleFiles;

        private EvalManifest(
                String schemaVersion,
                String suiteId,
                String datasetVersion,
                List<Path> trackedCaseFiles,
                List<Path> generationRuleFiles) {
            this.schemaVersion = schemaVersion;
            this.suiteId = suiteId;
            this.datasetVersion = datasetVersion;
            this.trackedCaseFiles = trackedCaseFiles;
            this.generationRuleFiles = generationRuleFiles;
        }

        public String getSchemaVersion() { return schemaVersion; }
        public String getSuiteId() { return suiteId; }
        public String getDatasetVersion() { return datasetVersion; }
        public List<Path> getTrackedCaseFiles() { return trackedCaseFiles; }
        public List<Path> getGenerationRuleFiles() { return generationRuleFiles; }
    }

    public static final class ManifestDocument {
        public String schemaVersion;
        public String suiteId;
        public String datasetVersion;
        public List<String> trackedCaseFiles;
        public List<String> generationRuleFiles;
        public Challenge challenge;

        @JsonCreator
        public ManifestDocument(
                @JsonProperty("schemaVersion") String schemaVersion,
                @JsonProperty("suiteId") String suiteId,
                @JsonProperty("datasetVersion") String datasetVersion,
                @JsonProperty("trackedCaseFiles") List<String> trackedCaseFiles,
                @JsonProperty("generationRuleFiles") List<String> generationRuleFiles,
                @JsonProperty("challenge") Challenge challenge) {
            this.schemaVersion = schemaVersion;
            this.suiteId = suiteId;
            this.datasetVersion = datasetVersion;
            this.trackedCaseFiles = trackedCaseFiles;
            this.generationRuleFiles = generationRuleFiles;
            this.challenge = challenge;
        }
    }

    public static final class Challenge {
        public String source;
        public boolean pathStoredInRepository;

        @JsonCreator
        public Challenge(
                @JsonProperty("source") String source,
                @JsonProperty("pathStoredInRepository") boolean pathStoredInRepository) {
            this.source = source;
            this.pathStoredInRepository = pathStoredInRepository;
        }
    }
}
