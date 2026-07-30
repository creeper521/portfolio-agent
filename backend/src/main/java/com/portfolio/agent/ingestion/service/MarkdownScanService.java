package com.portfolio.agent.ingestion.service;

import com.portfolio.agent.ingestion.domain.MarkdownScanEntry;
import com.portfolio.agent.ingestion.domain.MarkdownScanReport;
import com.portfolio.agent.ingestion.domain.SourceDocumentStatus;
import com.portfolio.agent.ingestion.gateway.SourceDocumentCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class MarkdownScanService {

    private final SourceDocumentCatalog catalog;

    public MarkdownScanService(SourceDocumentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public MarkdownScanReport scan(Path root) {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("scan root must be a directory");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<MarkdownScanEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (Stream<Path> paths = Files.walk(normalizedRoot)) {
            paths.filter(this::isMarkdownFile)
                    .forEach(path -> scanFile(normalizedRoot, path, seen, entries));
        } catch (IOException exception) {
            throw new IllegalStateException("unable to scan markdown root", exception);
        }
        addMissing(seen, entries);
        entries.sort(Comparator.comparing(MarkdownScanEntry::getRelativePath));
        return new MarkdownScanReport(entries);
    }

    private boolean isMarkdownFile(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".md");
    }

    private void scanFile(
            Path root,
            Path path,
            Set<String> seen,
            List<MarkdownScanEntry> entries) {
        String relativePath = relativePath(root, path);
        seen.add(relativePath);
        try {
            Path realRoot = root.toRealPath();
            Path realFile = path.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                entries.add(new MarkdownScanEntry(
                        relativePath,
                        SourceDocumentStatus.BLOCKED,
                        null,
                        "PATH_OUTSIDE_ROOT"));
                return;
            }
            String hash = sha256(realFile);
            SourceDocumentStatus status = catalog.contentHash(relativePath)
                    .map(previous -> previous.equals(hash)
                            ? SourceDocumentStatus.UNCHANGED
                            : SourceDocumentStatus.CHANGED)
                    .orElse(SourceDocumentStatus.ADDED);
            entries.add(new MarkdownScanEntry(relativePath, status, hash, null));
        } catch (IOException exception) {
            entries.add(new MarkdownScanEntry(
                    relativePath,
                    SourceDocumentStatus.FAILED,
                    null,
                    "FILE_READ_FAILED"));
        }
    }

    private void addMissing(
            Set<String> seen,
            List<MarkdownScanEntry> entries) {
        for (Map.Entry<String, String> known : catalog.knownDocuments().entrySet()) {
            if (!seen.contains(known.getKey())) {
                entries.add(new MarkdownScanEntry(
                        known.getKey(),
                        SourceDocumentStatus.MISSING,
                        known.getValue(),
                        null));
            }
        }
    }

    private String relativePath(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
