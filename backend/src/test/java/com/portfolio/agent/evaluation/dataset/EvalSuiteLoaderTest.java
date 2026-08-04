package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalSuiteLoaderTest {

    private final EvalSuiteLoader loader = new EvalSuiteLoader(new ObjectMapper());

    @Test
    void loadsAndSortsCasesWithoutExposingOracleAsInput() throws Exception {
        EvalSuite suite = loader.load(resource("/evaluation/valid-suite.json"));

        assertThat(suite.getDatasetVersion()).isEqualTo("2026-08-04.1");
        assertThat(suite.getCases()).extracting(EvalCase::getId)
                .containsExactly("answer.sql.001", "route.sql.001");
        assertThat(suite.getCases().get(1).getInputMessages())
                .extracting(EvalMessage::getContent)
                .containsExactly("你做过数据库风险排查相关的项目吗？");
        assertThat(suite.getCases().get(1).getExpectedSubjects())
                .extracting(EvalSubjectRef::getSlug)
                .containsExactly("sql-audit-project");
    }

    @Test
    void rejectsUnknownProperties() {
        byte[] source = "{\"schemaVersion\":\"1.0\",\"suiteId\":\"x\",\"datasetVersion\":\"2026-08-04.1\",\"cases\":[],\"unexpected\":true}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> loader.load(source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid evaluation suite");
    }

    @Test
    void rejectsDuplicateCaseIdsAndTrackedChallengeContent() {
        assertThatThrownBy(() -> loader.load(resource("/evaluation/invalid-duplicate.json")))
                .hasRootCauseMessage("case id must be unique: route.sql.001");
        assertThatThrownBy(() -> loader.load(resource("/evaluation/invalid-tracked-challenge.json")))
                .hasRootCauseMessage("CHALLENGE cases require external source mode");
    }

    @Test
    void permitsChallengeCasesOnlyWhenExternalSourceModeIsExplicit() throws Exception {
        EvalSuiteLoader externalLoader = new EvalSuiteLoader(new ObjectMapper(), true);

        EvalSuite suite = externalLoader.load(resource("/evaluation/invalid-tracked-challenge.json"));

        assertThat(suite.getCases()).hasSize(1);
    }

    @Test
    void rejectsMissingGeneratedFromBundle() throws Exception {
        assertThatThrownBy(() -> loader.load(resource(
                "/evaluation/invalid-missing-generated-from-bundle.json")))
                .hasRootCauseMessage("generatedFromBundle must be specified");
    }

    @Test
    void definesClosedPolicyThresholdsForReleaseGates() throws Exception {
        JsonNode schema = new ObjectMapper().readTree(Files.readAllBytes(policySchemaPath()));
        JsonNode blocking = schema.at("/$defs/thresholds/properties/blocking");
        JsonNode properties = blocking.path("properties");

        assertThat(blocking.path("additionalProperties").asBoolean()).isFalse();
        assertThat(properties.path("publicSubjectSmokeCoverageMinimum").path("minimum").asDouble())
                .isEqualTo(1.0d);
        assertThat(properties.path("deepSemanticRouteTopOneMinimum").path("minimum").asDouble())
                .isEqualTo(0.90d);
        assertThat(properties.path("providerFailureRateMaximum").path("maximum").asDouble())
                .isEqualTo(0.02d);
        assertThat(properties.path("falseSufficientMaximum").path("maximum").asDouble())
                .isZero();
        assertThat(properties.path("providerP95LatencyMaximumMs").path("minimum").asDouble())
                .isZero();
        assertThat(blocking.path("required")).extracting(JsonNode::asText)
                .contains("providerP95LatencyMaximumMs");
        assertThat(schema.at("/$defs/thresholds/properties/scored/properties/answerQualityPassRateMinimum/minimum")
                .asDouble()).isEqualTo(0.0d);
    }

    @Test
    void rejectsPolicyFixturesMissingOrNegativeProviderP95LatencyBudget() throws Exception {
        assertThat(validateProviderP95Latency(
                policyFixture("/evaluation/invalid-policy-missing-p95-latency.json")))
                .contains("providerP95LatencyMaximumMs is required");
        assertThat(validateProviderP95Latency(
                policyFixture("/evaluation/invalid-policy-negative-p95-latency.json")))
                .contains("providerP95LatencyMaximumMs must be at least 0");
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream input = EvalSuiteLoaderTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return input.readAllBytes();
        }
    }

    private Path policySchemaPath() throws IOException {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        for (int index = 0; index < 4; index++) {
            Path schema = candidate.resolve("governance/portfolio-governance/schemas/eval-policy.schema.json");
            if (Files.isRegularFile(schema)) {
                return schema;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        throw new IOException("Missing evaluation policy schema");
    }

    private JsonNode policyFixture(String path) throws IOException {
        return new ObjectMapper().readTree(resource(path));
    }

    private List<String> validateProviderP95Latency(JsonNode policy) throws Exception {
        JsonNode blockingSchema = new ObjectMapper().readTree(Files.readAllBytes(policySchemaPath()))
                .at("/$defs/thresholds/properties/blocking");
        JsonNode required = blockingSchema.path("required");
        JsonNode value = policy.at("/thresholds/blocking/providerP95LatencyMaximumMs");
        List<String> violations = new ArrayList<String>();
        boolean requiredField = false;
        for (JsonNode field : required) {
            if ("providerP95LatencyMaximumMs".equals(field.asText())) {
                requiredField = true;
            }
        }
        if (requiredField && value.isMissingNode()) {
            violations.add("providerP95LatencyMaximumMs is required");
        }
        JsonNode minimum = blockingSchema.path("properties")
                .path("providerP95LatencyMaximumMs").path("minimum");
        if (value.isNumber() && minimum.isNumber()
                && value.asDouble() < minimum.asDouble()) {
            violations.add("providerP95LatencyMaximumMs must be at least " + minimum.asInt());
        }
        return violations;
    }
}
