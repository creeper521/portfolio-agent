package com.portfolio.agent.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PublicBundleDirectoryReader {

    private static final List<String> FILE_NAMES = List.of(
            "checksums.json",
            "keyword-index.json",
            "manifest.json",
            "portfolio.json",
            "presentation.json",
            "rag-documents.jsonl",
            "vector-index.bin"
    );

    public Map<String, byte[]> read(Path supplied) throws IOException {
        if (supplied == null) {
            throw new IllegalArgumentException("bundle directory is required");
        }
        Path directory = supplied.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new IllegalArgumentException("bundle directory is invalid");
        }
        List<Path> entries;
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            entries = stream.sorted().toList();
        }
        if (entries.size() != FILE_NAMES.size()) {
            throw new IllegalArgumentException("bundle file set is not closed");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Path entry : entries) {
            Path normalized = entry.toAbsolutePath().normalize();
            String name = normalized.getFileName().toString();
            if (!normalized.getParent().equals(directory)
                    || !normalized.startsWith(directory)
                    || !FILE_NAMES.contains(name)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(normalized)) {
                throw new IllegalArgumentException("bundle file set is not closed");
            }
            files.put(name, Files.readAllBytes(normalized));
        }
        if (!files.keySet().equals(Set.copyOf(FILE_NAMES))) {
            throw new IllegalArgumentException("bundle file set is not closed");
        }
        return files;
    }
}
