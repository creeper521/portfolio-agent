package com.portfolio.agent.evaluation.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.application.EvalHarness;
import com.portfolio.agent.evaluation.dataset.EvalPolicyLoader;
import com.portfolio.agent.evaluation.dataset.EvalSuiteLoader;
import com.portfolio.agent.evaluation.execution.BundleContractEvalExecutor;
import com.portfolio.agent.evaluation.execution.EvalExecutor;
import com.portfolio.agent.evaluation.execution.SubjectInternalRetrievalExecutor;
import com.portfolio.agent.evaluation.execution.FullCorpusRetrievalExecutor;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.evaluation.grading.DeterministicEvalGrader;
import com.portfolio.agent.evaluation.reporting.EvalBaselineComparator;
import com.portfolio.agent.evaluation.reporting.EvalMetricAggregator;
import com.portfolio.agent.evaluation.reporting.EvalReportJsonWriter;
import com.portfolio.agent.evaluation.reporting.EvalReportMarkdownRenderer;
import com.portfolio.agent.evaluation.reporting.EvalVerdictPolicy;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit bootstrap for the eval CLI. The evaluation package intentionally
 * registers no Spring components.
 */
public final class EvalCliBootstrap {

    /**
     * Highest-precedence fail-closed arguments applied to every Spring context
     * the eval CLI assembles. Even if the environment pre-sets
     * PORTFOLIO_MODEL_ENABLED=true, command-line properties win, so validate
     * and offline processes can never indirectly enable the real provider.
     */
    static final String[] FORCED_OFF_ARGS = {
            "--spring.main.web-application-type=none",
            "--portfolio.conversational-agent.enabled=false",
            "--portfolio.conversational-agent.visitor-data-policy-approved=false",
            "--portfolio.retrieval.profile=KEYWORD_ONLY"
    };

    private final ObjectMapper mapper;
    private final boolean providerAuthorized;
    private final boolean springEnabled;
    private org.springframework.context.ConfigurableApplicationContext springContext;

    public EvalCliBootstrap(boolean providerAuthorized) {
        this(providerAuthorized, springEnabledByDefault());
    }

    EvalCliBootstrap(boolean providerAuthorized, boolean springEnabled) {
        this.mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.providerAuthorized = providerAuthorized;
        this.springEnabled = springEnabled;
    }

    private static boolean springEnabledByDefault() {
        return !"false".equalsIgnoreCase(
                System.getProperty("portfolio.eval.cli.spring", "true"));
    }

    public EvalSuiteLoader createSuiteLoader() {
        return new EvalSuiteLoader(mapper);
    }

    public EvalPolicyLoader createPolicyLoader() {
        return new EvalPolicyLoader();
    }

    public RuntimeContentSnapshot loadBundle() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json",
                "rag-documents.jsonl", "keyword-index.json",
                "vector-index.bin", "checksums.json")) {
            files.put(name, readResource("public-data/bundle/" + name));
        }
        PublicBundleLoader loader = new PublicBundleLoader(
                mapper,
                new com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator(),
                java.time.Clock.systemUTC());
        return loader.load(files);
    }

    public EvalHarness createHarness(RuntimeContentSnapshot bundle) {
        return createHarness(bundle, null, false);
    }

    public EvalHarness createHarness(
            RuntimeContentSnapshot bundle,
            String baseUrl,
            boolean withIntelligence) {
        List<EvalExecutor> executors = new ArrayList<>();
        executors.add(new BundleContractEvalExecutor(bundle));
        executors.add(new SubjectInternalRetrievalExecutor(bundle));
        LocalEmbeddingPort offlineEmbedding = query -> {
            throw new LocalEmbeddingFailureException("LOCAL_EMBEDDING_DISABLED");
        };
        executors.add(new FullCorpusRetrievalExecutor(
                bundle, RetrievalPolicy.currentRelease(), new RetrievalQueryNormalizer(),
                new KeywordRetriever(), new VectorRetriever(), new ReciprocalRankFusion(),
                new RetrievalContextValidator(), offlineEmbedding));
        if (withIntelligence) {
            com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor
                    portfolioExecutor = createPortfolioExecutor();
            if (portfolioExecutor != null) {
                executors.add(new com.portfolio.agent.evaluation.execution
                        .PortfolioEvalExecutor(portfolioExecutor, bundle));
            }
            if (baseUrl != null && !baseUrl.isBlank()) {
                executors.add(new com.portfolio.agent.evaluation.execution
                        .HttpEvalExecutor(
                                new com.portfolio.agent.evaluation.execution
                                        .JdkEvalAnswerClient(mapper),
                                baseUrl, bundle));
            }
        }
        return new EvalHarness(
                executors,
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                bundle);
    }

    private com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor
            createPortfolioExecutor() {
        org.springframework.context.ConfigurableApplicationContext context =
                springContext();
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(
                    com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor.class);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    /**
     * Lazily assembled, cached Spring context used by both the intelligence
     * pipeline and the real provider seam. Always fail-closed: the model,
     * conversation and data-policy flags are forced off at the highest
     * precedence, so this context can never enable the real provider on its
     * own; explicit --authorize-real-provider decides whether the real seam
     * bean is used by the provider executor.
     */
    private org.springframework.context.ConfigurableApplicationContext
            springContext() {
        if (!springEnabled) {
            return null;
        }
        if (springContext == null) {
            try {
                org.springframework.boot.builder.SpringApplicationBuilder builder =
                        new org.springframework.boot.builder.SpringApplicationBuilder(
                                com.portfolio.agent.PortfolioAgentApplication.class)
                                .web(org.springframework.boot.WebApplicationType.NONE)
                                .properties("spring.main.banner-mode=off");
                String[] contextArgs = providerAuthorized
                        ? new String[]{
                                "--spring.main.web-application-type=none",
                                "--portfolio.retrieval.profile=KEYWORD_ONLY"}
                        : FORCED_OFF_ARGS;
                springContext = builder.run(contextArgs);
            } catch (RuntimeException failure) {
                return null;
            }
        }
        return springContext;
    }

    public EvalReportJsonWriter createJsonWriter() {
        return new EvalReportJsonWriter();
    }

    public EvalReportMarkdownRenderer createMarkdownRenderer() {
        return new EvalReportMarkdownRenderer();
    }

    private byte[] readResource(String path) {
        try (InputStream stream = EvalCliBootstrap.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing classpath resource: " + path);
            }
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "unable to read classpath resource: " + path, failure);
        }
    }
}
