package com.portfolio.agent.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfolio.agent.PortfolioAgentApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@AutoConfigureMockMvc
class PortfolioSelectionSurfaceRemovalTest {

    private static final Path SELECTION_SOURCE = Path.of(
            "src/main/java/com/portfolio/agent/selection");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicSelectionEndpointHasNoMapping() throws Exception {
        mockMvc.perform(post("/api/portfolio-selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void productionSourceContainsNoSelectionHttpSurface() throws Exception {
        assertThat(SELECTION_SOURCE.resolve("controller/PortfolioSelectionController.java"))
                .doesNotExist();
        assertThat(SELECTION_SOURCE.resolve("mapper/PortfolioSelectionResponseMapper.java"))
                .doesNotExist();
        assertThat(SELECTION_SOURCE.resolve(
                "adapter/postgres/PostgresSelectionConfiguration.java"))
                .doesNotExist();
        try (java.util.stream.Stream<Path> files = Files.walk(SELECTION_SOURCE)) {
            List<Path> javaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            assertThat(javaFiles).noneMatch(path -> path.startsWith(SELECTION_SOURCE.resolve("dto")));
            List<String> source = javaFiles.stream()
                    .map(this::read)
                    .toList();
            assertThat(source).noneMatch(content ->
                    content.contains("/api/portfolio-selections")
                            || content.contains("PortfolioSelectionResponseMapper"));
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("cannot inspect production source", exception);
        }
    }
}
