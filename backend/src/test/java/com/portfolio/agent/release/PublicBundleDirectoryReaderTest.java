package com.portfolio.agent.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicBundleDirectoryReaderTest {

    @TempDir
    Path temporary;

    @Test
    void rejectsDirectorySymlinkWhenPlatformSupportsIt() throws Exception {
        Path target = Files.createDirectory(temporary.resolve("target"));
        Path link = temporary.resolve("link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links unavailable");
        }

        assertThatThrownBy(() -> new PublicBundleDirectoryReader().read(link))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRequiredFileThatEscapesThroughSymbolicLinkWhenSupported()
            throws Exception {
        Path bundle = Files.createDirectory(temporary.resolve("bundle"));
        Path fixture = fixtureDirectory();
        for (String name : List.of(
                "checksums.json", "keyword-index.json", "manifest.json",
                "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "vector-index.bin")) {
            Files.copy(fixture.resolve(name), bundle.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Files.delete(bundle.resolve("portfolio.json"));
        Path outside = Files.writeString(temporary.resolve("outside.json"), "{}");
        try {
            Files.createSymbolicLink(bundle.resolve("portfolio.json"), outside);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links unavailable");
        }

        assertThatThrownBy(() -> new PublicBundleDirectoryReader().read(bundle))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Path fixtureDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path root = Files.isDirectory(current.resolve("backend")) ? current : current.getParent();
        return root.resolve("backend/src/main/resources/public-data/bundle");
    }
}
