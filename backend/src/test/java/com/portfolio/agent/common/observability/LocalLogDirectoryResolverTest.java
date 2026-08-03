package com.portfolio.agent.common.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalLogDirectoryResolverTest {

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesTheSameRepositoryLogsFromRootAndBackendDirectory() throws Exception {
        Path repository = repository("repo");
        LocalLogDirectoryResolver resolver = new LocalLogDirectoryResolver();

        assertThat(resolver.resolve(repository, null))
                .contains(repository.resolve("logs").toAbsolutePath().normalize());
        assertThat(resolver.resolve(repository.resolve("backend"), null))
                .contains(repository.resolve("logs").toAbsolutePath().normalize());
    }

    @Test
    void explicitSafeDirectoryWinsOverRepositoryDiscovery() throws Exception {
        Path repository = repository("repo");
        Path explicit = tempDirectory.resolve("custom-logs");

        assertThat(new LocalLogDirectoryResolver().resolve(repository, explicit.toString()))
                .contains(explicit.toAbsolutePath().normalize());
    }

    @Test
    void rejectsFilesystemRootAndUnknownLayouts() throws Exception {
        Path unknown = Files.createDirectories(tempDirectory.resolve("unknown"));
        Path root = unknown.toAbsolutePath().getRoot();
        LocalLogDirectoryResolver resolver = new LocalLogDirectoryResolver();

        assertThat(resolver.resolve(unknown, root.toString())).isEmpty();
        assertThat(resolver.resolve(unknown, null)).isEmpty();
    }

    private Path repository(String name) throws Exception {
        Path repository = Files.createDirectories(tempDirectory.resolve(name));
        Files.createDirectories(repository.resolve(".git"));
        Files.createDirectories(repository.resolve("backend"));
        Files.writeString(repository.resolve("backend/pom.xml"), "<project/>");
        Files.createDirectories(repository.resolve("frontend"));
        Files.writeString(repository.resolve("frontend/package.json"), "{}");
        return repository;
    }
}
