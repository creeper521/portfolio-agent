package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.portfolio.domain.ReleaseManifest;
import com.portfolio.agent.portfolio.domain.PresentationSnapshot;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeVectorIndex;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PublicBundleLoader {

    private static final Set<String> LEGACY_FILES = Set.of(
            "manifest.json", "portfolio.json", "presentation.json", "checksums.json");
    private static final Set<String> RETRIEVAL_FILES = Set.of(
            "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
            "keyword-index.json", "vector-index.bin", "checksums.json");
    private static final String APPLICATION_VERSION = "0.1.0";
    private static final Set<String> PORTFOLIO_FIELDS = Set.of(
            "schemaVersion", "contentVersion", "owner", "internshipPeriod", "projects",
            "claims", "evidence", "claimEvidenceLinks", "timelineEvents", "questionPresets");

    private final ObjectMapper objectMapper;
    private final PortfolioSnapshotValidator validator;
    private final Clock clock;

    public PublicBundleLoader(
            ObjectMapper objectMapper,
            PortfolioSnapshotValidator validator,
            Clock clock
    ) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.validator = validator;
        this.clock = clock;
    }

    public RuntimeContentSnapshot load(Map<String, byte[]> files) {
        try {
            require(files != null && (files.keySet().equals(LEGACY_FILES)
                            || files.keySet().equals(RETRIEVAL_FILES)),
                    "bundle file set is not closed");
            ReleaseManifest manifest = read(files, "manifest.json", ReleaseManifest.class);
            boolean hasRetrieval = manifest.getRetrieval() != null;
            require(hasRetrieval == files.keySet().equals(RETRIEVAL_FILES),
                    "bundle file set does not match retrieval manifest");
            require("2.0".equals(manifest.getSchemaVersion()), "unsupported manifest schemaVersion");
            require("portfolio.json".equals(manifest.getFactsFile()), "invalid factsFile");
            require("presentation.json".equals(manifest.getPresentationFile()),
                    "invalid presentationFile");
            require("checksums.json".equals(manifest.getChecksumsFile()), "invalid checksumsFile");
            require(hasText(manifest.getApprovalId()) && startsWithSha256(manifest.getApprovalDigest()),
                    "manifest approval metadata is invalid");
            require(isCompatible(manifest.getMinimumApplicationVersion()),
                    "minimumApplicationVersion is not compatible");

            Checksums checksums = read(files, "checksums.json", Checksums.class);
            require(manifest.getSchemaVersion().equals(checksums.getSchemaVersion()),
                    "checksums schemaVersion mismatch");
            require(manifest.getContentVersion().equals(checksums.getContentVersion()),
                    "checksums contentVersion mismatch");
            Set<String> expectedChecksums = hasRetrieval
                    ? Set.of("portfolio.json", "presentation.json", "rag-documents.jsonl",
                            "keyword-index.json", "vector-index.bin")
                    : Set.of("portfolio.json", "presentation.json");
            require(checksums.getFiles().keySet().equals(expectedChecksums),
                    "checksums file set is not closed");
            for (String name : expectedChecksums) {
                verifyChecksum(files, checksums, name);
            }

            String candidateHash = BundleHashCalculator.candidatePayloadHash(files);
            require(candidateHash.equals(manifest.getCandidatePayloadHash()),
                    "candidatePayloadHash mismatch");

            requireExactTopLevelFields(files.get("portfolio.json"), PORTFOLIO_FIELDS,
                    "portfolio.json");
            PortfolioSnapshot content = read(files, "portfolio.json", PortfolioSnapshot.class);
            PresentationSnapshot presentation = read(
                    files, "presentation.json", PresentationSnapshot.class);
            require(manifest.getSchemaVersion().equals(content.getSchemaVersion())
                            && manifest.getSchemaVersion().equals(presentation.getSchemaVersion()),
                    "schemaVersion mismatch across bundle files");
            require(manifest.getContentVersion().equals(content.getContentVersion())
                            && manifest.getContentVersion().equals(presentation.getContentVersion()),
                    "contentVersion mismatch across bundle files");
            require(manifest.getCounts() != null && manifest.getCounts().matches(content),
                    "manifest counts mismatch");

            PortfolioSnapshot published = content.withPublishedAt(manifest.getPublishedAt());
            validator.validate(published);
            RuntimeRetrievalContent retrievalContent = hasRetrieval
                    ? readRetrievalContent(files, manifest)
                    : null;
            return new RuntimeContentSnapshot(
                    published,
                    BundleHashCalculator.runtimeBundleHash(
                            files.get("manifest.json"), files.get("checksums.json")),
                    clock.instant(),
                    retrievalContent
            );
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof InvalidPortfolioSnapshotException invalid) {
                throw invalid;
            }
            throw new InvalidPortfolioSnapshotException(
                    "unable to load public release bundle: " + exception.getMessage(), exception);
        }
    }

    private RuntimeRetrievalContent readRetrievalContent(
            Map<String, byte[]> files,
            ReleaseManifest manifest
    ) throws IOException {
        byte[] source = files.get("rag-documents.jsonl");
        List<RagDocument> documents = new ArrayList<>();
        String[] lines = new String(source, java.nio.charset.StandardCharsets.UTF_8)
                .split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isEmpty() && index == lines.length - 1) {
                continue;
            }
            require(!lines[index].isBlank(), "rag-documents.jsonl contains a blank line");
            RagDocument document = objectMapper.readValue(lines[index], RagDocument.class);
            require(manifest.getContentVersion().equals(document.getContentVersion()),
                    "rag document contentVersion mismatch");
            documents.add(document);
        }
        require(manifest.getRetrieval().getChunkCount() == documents.size(),
                "retrieval chunkCount mismatch");
        require(BundleHashCalculator.sha256(source)
                        .equals(manifest.getRetrieval().getChunkSetHash()),
                "retrieval chunkSetHash mismatch");
        KeywordIndexFile keywordFile = read(
                files, "keyword-index.json", KeywordIndexFile.class);
        require(manifest.getRetrieval().getKeywordIndexFormatVersion()
                        .equals(keywordFile.getFormatVersion()),
                "keyword index formatVersion mismatch");
        require(manifest.getRetrieval().getNormalizationVersion()
                        .equals(keywordFile.getNormalizationVersion()),
                "keyword index normalizationVersion mismatch");
        require(keywordFile.getDocumentCount() == documents.size(),
                "keyword index documentCount mismatch");
        VectorIndexFile vectorFile = new VectorIndexCodec().decode(
                files.get("vector-index.bin"), manifest.getRetrieval().getDimension());
        Set<String> chunkIds = documents.stream()
                .map(RagDocument::getChunkId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        require(keywordFile.getDocuments().stream()
                        .map(KeywordIndexFile.DocumentEntry::getChunkId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())
                        .equals(chunkIds),
                "keyword index chunk set mismatch");
        require(vectorFile.getVectors().keySet().equals(chunkIds),
                "vector index chunk set mismatch");
        return new RuntimeRetrievalContent(
                manifest.getRetrieval(), documents,
                toRuntimeKeywordIndex(keywordFile),
                new RuntimeVectorIndex(vectorFile.getDimension(), vectorFile.getVectors()));
    }

    private RuntimeKeywordIndex toRuntimeKeywordIndex(KeywordIndexFile source) {
        List<RuntimeKeywordIndex.DocumentEntry> documents = source.getDocuments().stream()
                .map(item -> new RuntimeKeywordIndex.DocumentEntry(
                        item.getChunkId(), item.getDocumentLength(), item.getTermFrequencies()))
                .toList();
        return new RuntimeKeywordIndex(
                source.getDocumentCount(), source.getAverageDocumentLength(),
                documents, source.getDocumentFrequencies());
    }

    private <T> T read(Map<String, byte[]> files, String name, Class<T> type) throws IOException {
        byte[] bytes = files.get(name);
        require(bytes != null, "missing bundle file: " + name);
        return objectMapper.readValue(bytes, type);
    }

    private void requireExactTopLevelFields(
            byte[] bytes,
            Set<String> allowedFields,
            String fileName
    ) throws IOException {
        JsonNode root = objectMapper.readTree(bytes);
        require(root != null && root.isObject(), fileName + " must contain a JSON object");
        Set<String> actualFields = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(actualFields::add);
        require(allowedFields.containsAll(actualFields), fileName + " field set is not canonical");
    }

    private void verifyChecksum(Map<String, byte[]> files, Checksums checksums, String name) {
        require(BundleHashCalculator.sha256(files.get(name)).equals(checksums.getFiles().get(name)),
                "checksum mismatch: " + name);
    }

    private boolean startsWithSha256(String value) {
        return value != null && value.startsWith("sha256:") && value.length() > "sha256:".length();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isCompatible(String minimumVersion) {
        int[] minimum = parseVersion(minimumVersion);
        int[] current = parseVersion(APPLICATION_VERSION);
        if (minimum == null || current == null) return false;
        for (int index = 0; index < current.length; index++) {
            if (minimum[index] < current[index]) return true;
            if (minimum[index] > current[index]) return false;
        }
        return true;
    }

    private int[] parseVersion(String value) {
        if (value == null || !value.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = value.split("\\.");
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPortfolioSnapshotException(message);
        }
    }

    private static final class Checksums {
        private final String schemaVersion;
        private final String contentVersion;
        private final Map<String, String> files;

        @JsonCreator
        private Checksums(@JsonProperty("schemaVersion") String schemaVersion,
                @JsonProperty("contentVersion") String contentVersion,
                @JsonProperty("files") Map<String, String> files) {
            this.schemaVersion = schemaVersion;
            this.contentVersion = contentVersion;
            this.files = Map.copyOf(files);
        }
        private String getSchemaVersion() { return schemaVersion; }
        private String getContentVersion() { return contentVersion; }
        private Map<String, String> getFiles() { return files; }
    }
}
