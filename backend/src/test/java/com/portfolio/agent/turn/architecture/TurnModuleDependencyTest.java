package com.portfolio.agent.turn.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TurnModuleDependencyTest {
    @Test void coreModulesDoNotDependOnHttpDtosOrReverseLifecycleEdges() throws Exception {
        Path root = Path.of("src/main/java/com/portfolio/agent/turn");
        List<String> planning = sources(root.resolve("planning"));
        List<String> execution = sources(root.resolve("execution"));
        List<String> capability = sources(root.resolve("capability"));
        List<String> projection = sources(root.resolve("projection"));
        List<String> lifecycle = sources(root.resolve("lifecycle"));

        assertThat(planning).noneMatch(value -> {
            String allowed = value
                    .replace("import com.portfolio.agent.turn.execution.TurnDeadline;", "")
                    .replaceAll(
                            "import com\\.portfolio\\.agent\\.turn\\.capability\\.portfolio"
                                    + "\\.knowledge\\.[A-Za-z0-9_]+;",
                            "");
            return allowed.contains("turn.api")
                    || allowed.contains("turn.execution")
                    || allowed.contains("turn.capability");
        });
        assertThat(execution).noneMatch(value -> value.contains("turn.api")
                || value.contains("turn.lifecycle"));
        assertThat(capability).noneMatch(value -> value.contains("turn.api")
                || value.contains("turn.lifecycle"));
        assertThat(projection).noneMatch(value -> value.contains("turn.api")
                || value.contains("turn.infrastructure") || value.contains("answer.dto"));
        assertThat(lifecycle).noneMatch(value -> value.contains("turn.api"));
    }

    @Test void productionTurnModuleContainsNoMigrationOrVersionBridgeNames() throws Exception {
        List<String> sources = sources(Path.of("src/main/java/com/portfolio/agent/turn"));
        assertThat(sources).noneMatch(value ->
                value.matches("(?s).*\\b(Legacy|Compatibility|Migration|Shadow|stp-v[123])\\b.*"));
    }

    @Test void stateAndModelInfrastructureDoNotDependOnLegacyAnswerOwners() throws Exception {
        List<String> turnSources = sources(Path.of("src/main/java/com/portfolio/agent/turn"));
        assertThat(turnSources).noneMatch(source ->
                source.contains("com.portfolio.agent.answer.context")
                        || source.contains("com.portfolio.agent.answer.adapter.model")
                        || source.contains("com.portfolio.agent.answer.domain.ConversationProviderAccess")
                        || source.contains("com.portfolio.agent.answer.domain.ModelPolicy")
                        || source.contains("com.portfolio.agent.answer.domain.ModelProviderKind")
                        || source.contains("com.portfolio.agent.answer.gateway.ModelProviderRegistry")
                        || source.contains("com.portfolio.agent.answer.runtime.ModelOperation")
                        || source.contains("com.portfolio.agent.answer.runtime.OperationMode")
                        || source.contains("com.portfolio.agent.answer.runtime.OperationReadiness"));

        List<String> answerStateAndModelConfiguration = Stream.of(
                        Path.of("src/main/java/com/portfolio/agent/answer/context"),
                        Path.of("src/main/java/com/portfolio/agent/answer/adapter/model"))
                .filter(Files::exists)
                .flatMap(path -> sourcesUnchecked(path).stream())
                .toList();
        assertThat(answerStateAndModelConfiguration)
                .noneMatch(source -> source.contains("com.portfolio.agent.turn."));

        assertThat(sources(Path.of("src/main/java/com/portfolio/agent/turn/capability/general")))
                .noneMatch(source -> source.contains("com.portfolio.agent.infrastructure.model.policy"));
        assertThat(sources(Path.of("src/main/java/com/portfolio/agent/infrastructure/model/provider")))
                .noneMatch(source -> source.contains("com.portfolio.agent.infrastructure.model.configuration"));
    }

    @Test void turnProductionSourcesDoNotDependOnLegacyAnswerPackage() throws Exception {
        assertThat(sources(Path.of("src/main/java/com/portfolio/agent/turn")))
                .noneMatch(source -> source.contains("com.portfolio.agent.answer."));
    }

    @Test void retiredPortfolioBridgeTypesCannotBeReintroducedAnywhere() throws Exception {
        String retiredTypeDeclaration = "(?s).*\\b(?:class|interface|enum|record)\\s+"
                + "(?:PortfolioRetrieverAdapterSupport|PortfolioConditions|PortfolioRetrievalRequest|"
                + "PortfolioRetrievalResult|PortfolioRetrievedPassage|PortfolioTaskMode|"
                + "PortfolioRetrievalException|PortfolioRetrievalFailureKind|PortfolioRetriever)\\b.*";
        assertThat(sources(Path.of("src/main/java/com/portfolio/agent")))
                .noneMatch(source -> source.matches(retiredTypeDeclaration));
    }

    private List<String> sources(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (Exception failure) { throw new IllegalStateException(failure); }
                    }).toList();
        }
    }

    private List<String> sourcesUnchecked(Path root) {
        try {
            return sources(root);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
