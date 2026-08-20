package com.portfolio.agent.turn.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Task 11 的零旧表面门。 */
class LegacySurfaceManifestTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/portfolio/agent");

    @Test
    void answerProductionPackageHasBeenRemoved() {
        assertThat(SOURCE_ROOT.resolve("answer")).doesNotExist();
    }

    @Test
    void productionDoesNotReferenceAnswerOrSelectionByImportOrQualifiedName() throws Exception {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("com.portfolio.agent.answer.")
                                    || source.contains("com.portfolio.agent.selection.");
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .map(path -> SOURCE_ROOT.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        assertThat(offenders).as("legacy production imports").isEmpty();
    }

    @Test
    void selectionProductionPackageHasBeenRemoved() {
        assertThat(SOURCE_ROOT.resolve("selection")).doesNotExist();
    }
}
