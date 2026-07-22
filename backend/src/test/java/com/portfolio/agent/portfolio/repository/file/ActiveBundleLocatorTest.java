package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveBundleLocatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsOnlyTheCompleteVersionNamedByActive() throws Exception {
        Path releaseRoot = createReleaseRoot();
        PublicBundleLoader loader = new PublicBundleLoader(new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(), Clock.systemUTC());

        assertThat(new ActiveBundleLocator().load(releaseRoot, loader).getContentVersion())
                .isEqualTo("2026-07-21.1");
    }

    @Test
    void invalidConfiguredActiveFailsClosed() throws Exception {
        Path releaseRoot = createReleaseRoot();
        Files.writeString(releaseRoot.resolve("active"), "missing-version");

        assertThatThrownBy(() -> new ActiveBundleLocator().load(releaseRoot,
                new PublicBundleLoader(new ObjectMapper().findAndRegisterModules(),
                        new PortfolioSnapshotValidator(), Clock.systemUTC())))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("active");
    }

    private Path createReleaseRoot() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("releases"));
        Path version = Files.createDirectories(root.resolve("versions").resolve("2026-07-21.1"));
        for (String name : new String[]{"manifest.json", "portfolio.json", "presentation.json", "checksums.json"}) {
            ClassPathResource resource = new ClassPathResource("public-data/bundle/" + name);
            Files.write(version.resolve(name), resource.getContentAsByteArray());
        }
        Files.writeString(root.resolve("active"), "2026-07-21.1");
        return root;
    }
}
