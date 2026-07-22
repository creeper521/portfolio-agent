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
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;

public final class PublicBundleLoader {

    private static final Set<String> REQUIRED_FILES = Set.of(
            "manifest.json", "portfolio.json", "presentation.json", "checksums.json");
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
            require(files != null && files.keySet().equals(REQUIRED_FILES),
                    "bundle file set is not closed");
            ReleaseManifest manifest = read(files, "manifest.json", ReleaseManifest.class);
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
            require(checksums.getFiles().keySet().equals(
                    Set.of("portfolio.json", "presentation.json")),
                    "checksums file set is not closed");
            verifyChecksum(files, checksums, "portfolio.json");
            verifyChecksum(files, checksums, "presentation.json");

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
            return new RuntimeContentSnapshot(
                    published,
                    BundleHashCalculator.runtimeBundleHash(
                            files.get("manifest.json"), files.get("checksums.json")),
                    clock.instant()
            );
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof InvalidPortfolioSnapshotException invalid) {
                throw invalid;
            }
            throw new InvalidPortfolioSnapshotException(
                    "unable to load public release bundle: " + exception.getMessage(), exception);
        }
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
