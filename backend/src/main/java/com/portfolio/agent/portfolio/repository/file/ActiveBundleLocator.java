package com.portfolio.agent.portfolio.repository.file;

import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ActiveBundleLocator {
    private static final Set<String> LEGACY_FILES = Set.of(
            "manifest.json", "portfolio.json", "presentation.json", "checksums.json");
    private static final Set<String> RETRIEVAL_FILES = Set.of(
            "manifest.json", "portfolio.json", "presentation.json",
            "rag-documents.jsonl", "keyword-index.json", "vector-index.bin",
            "checksums.json");

    public RuntimeContentSnapshot load(Path releaseRoot, PublicBundleLoader loader) {
        try {
            Path root = releaseRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            require(!Files.isSymbolicLink(root), "release root must not be a symbolic link");
            Path activeFile = root.resolve("active");
            require(Files.isRegularFile(activeFile, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(activeFile), "active pointer is invalid");
            String contentVersion = Files.readString(activeFile, StandardCharsets.UTF_8).trim();
            require(contentVersion.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}\\.[0-9]+"),
                    "active contentVersion is invalid");
            Path versionsRoot = root.resolve("versions").toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path version = versionsRoot.resolve(contentVersion).toRealPath(LinkOption.NOFOLLOW_LINKS);
            require(version.startsWith(versionsRoot) && !Files.isSymbolicLink(version),
                    "active version escapes release root");
            Set<String> names;
            try (Stream<Path> entries = Files.list(version)) {
                names = entries.map(path -> path.getFileName().toString())
                        .collect(Collectors.toUnmodifiableSet());
            }
            require(names.equals(LEGACY_FILES) || names.equals(RETRIEVAL_FILES),
                    "active bundle file set is not closed");
            java.util.LinkedHashMap<String, byte[]> bytes = new java.util.LinkedHashMap<>();
            for (String name : names) {
                Path file = version.resolve(name);
                require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(file), "active bundle file is invalid");
                bytes.put(name, Files.readAllBytes(file));
            }
            return loader.load(Map.copyOf(bytes));
        } catch (IOException exception) {
            throw new InvalidPortfolioSnapshotException("unable to resolve active bundle", exception);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new InvalidPortfolioSnapshotException(message);
    }
}
