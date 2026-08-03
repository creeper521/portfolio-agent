package com.portfolio.agent.common.observability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class LocalLogDirectoryResolver {

    public Optional<Path> resolve(Path workingDirectory, String explicitDirectory) {
        if (explicitDirectory != null && !explicitDirectory.isBlank()) {
            return safePath(explicitDirectory);
        }
        if (workingDirectory == null) {
            return Optional.empty();
        }
        Path cursor;
        try {
            cursor = workingDirectory.toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        while (cursor != null) {
            if (isRepositoryRoot(cursor)) {
                return Optional.of(cursor.resolve("logs").toAbsolutePath().normalize());
            }
            cursor = cursor.getParent();
        }
        return Optional.empty();
    }

    private Optional<Path> safePath(String value) {
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            Path root = path.getRoot();
            if (root == null || path.equals(root)) {
                return Optional.empty();
            }
            return Optional.of(path);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean isRepositoryRoot(Path candidate) {
        return Files.exists(candidate.resolve(".git"))
                && Files.isRegularFile(candidate.resolve("backend/pom.xml"))
                && Files.isRegularFile(candidate.resolve("frontend/package.json"));
    }
}
