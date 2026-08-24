package com.portfolio.agent.evaluation.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.application.EvalHarness;
import com.portfolio.agent.evaluation.dataset.EvalPolicyLoader;
import com.portfolio.agent.evaluation.dataset.EvalSuiteLoader;
import com.portfolio.agent.evaluation.execution.BundleContractEvalExecutor;
import com.portfolio.agent.evaluation.execution.EvalExecutor;
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
     * PORTFOLIO_MODEL_RUNTIME_ENABLED=true, command-line properties win, so validate
     * and offline processes can never indirectly enable the real provider.
     */
    static final String[] FORCED_OFF_ARGS = {
            "--spring.main.web-application-type=none",
            "--portfolio.model-runtime.enabled=false",
            "--portfolio.retrieval.profile=KEYWORD_ONLY"
    };

    private final ObjectMapper mapper;
    public EvalCliBootstrap() {
        this.mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
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
        List<EvalExecutor> executors = new ArrayList<>();
        executors.add(new BundleContractEvalExecutor(bundle));
        return new EvalHarness(
                executors,
                new DeterministicEvalGrader(),
                new EvalMetricAggregator(),
                new EvalBaselineComparator(),
                new EvalVerdictPolicy(),
                bundle);
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
