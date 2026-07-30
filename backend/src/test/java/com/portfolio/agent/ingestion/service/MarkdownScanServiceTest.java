package com.portfolio.agent.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.ingestion.domain.MarkdownScanEntry;
import com.portfolio.agent.ingestion.domain.MarkdownScanReport;
import com.portfolio.agent.ingestion.domain.SourceDocumentStatus;
import com.portfolio.agent.ingestion.gateway.SourceDocumentCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownScanServiceTest {

    @TempDir
    Path root;

    @Test
    void previewsAddedChangedUnchangedAndMissingDocumentsWithoutMutatingCatalog() throws Exception {
        Files.writeString(root.resolve("unchanged.md"), "same", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("changed.md"), "new", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("added.md"), "added", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("ignored.txt"), "ignored", StandardCharsets.UTF_8);
        SourceDocumentCatalog catalog = new InMemoryCatalog(Map.of(
                "unchanged.md", sha256("same"),
                "changed.md", sha256("old"),
                "missing.md", sha256("missing")));

        MarkdownScanReport report = new MarkdownScanService(catalog).scan(root);

        assertThat(report.getEntries())
                .extracting(MarkdownScanEntry::getRelativePath, MarkdownScanEntry::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("added.md", SourceDocumentStatus.ADDED),
                        org.assertj.core.groups.Tuple.tuple("changed.md", SourceDocumentStatus.CHANGED),
                        org.assertj.core.groups.Tuple.tuple("missing.md", SourceDocumentStatus.MISSING),
                        org.assertj.core.groups.Tuple.tuple("unchanged.md", SourceDocumentStatus.UNCHANGED));
        assertThat(catalog.knownDocuments()).hasSize(3);
    }

    @Test
    void blocksMarkdownSymlinkThatEscapesTheSuppliedRoot() throws Exception {
        Path outside = Files.createTempFile("markdown-scan-outside", ".md");
        Path link = root.resolve("escape.md");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links unavailable");
        }

        MarkdownScanReport report = new MarkdownScanService(new InMemoryCatalog(Map.of())).scan(root);

        assertThat(report.getEntries()).extracting(MarkdownScanEntry::getStatus)
                .containsExactly(SourceDocumentStatus.BLOCKED);
        Files.deleteIfExists(outside);
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class InMemoryCatalog implements SourceDocumentCatalog {

        private final Map<String, String> documents;

        private InMemoryCatalog(Map<String, String> documents) {
            this.documents = Map.copyOf(documents);
        }

        @Override
        public Map<String, String> knownDocuments() {
            return documents;
        }

        @Override
        public Optional<String> contentHash(String relativePath) {
            return Optional.ofNullable(documents.get(relativePath));
        }
    }
}
