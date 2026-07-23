package com.portfolio.agent.release;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalBundleCompilerCliTest {

    @TempDir
    Path temporary;

    @Test
    void descriptorIdentityIsStableAndContainsNoCredential() throws Exception {
        assertThat(RetrievalBundleCompilerCli.descriptorHash())
                .matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void releaseCompilersRejectSchemaThreeWithoutCasesThroughCentralReader()
            throws Exception {
        Path portfolio = temporary.resolve("portfolio.json");
        Files.writeString(portfolio, """
                {"schemaVersion":"3.0","contentVersion":"content-1",
                "owner":{"name":"Owner","role":"Engineer","summary":"Summary",
                "githubUrl":null,"email":null,"resumeUrl":null},
                "projects":[],"claims":[],"evidence":[],"claimEvidenceLinks":[],
                "timelineEvents":[],"questionPresets":[]}
                """);
        Path model = temporary.resolve("model");
        Files.createDirectory(model);

        assertThatThrownBy(() -> RagDocumentCompilerCli.compile(
                portfolio, temporary.resolve("rag-documents.jsonl"),
                LocalDate.of(2026, 7, 23)))
                .hasMessageContaining("cases is required for schemaVersion 3.0");
        assertThatThrownBy(() -> RetrievalBundleCompilerCli.compile(
                portfolio, model, temporary.resolve("retrieval"),
                LocalDate.of(2026, 7, 23)))
                .hasMessageContaining("cases is required for schemaVersion 3.0");
    }

    @Test
    void compilesRealLocalArtifactsAtomicallyWhenThePinnedModelIsInstalled()
            throws Exception {
        Path root = projectRoot();
        Path model = root.resolve("runtime-models/bge-small-zh-v1.5");
        Assumptions.assumeTrue(Files.isRegularFile(
                model.resolve("onnx/model_quantized.onnx")));
        Path portfolio = root.resolve(
                "backend/src/main/resources/public-data/bundle/portfolio.json");
        Path output = temporary.resolve("retrieval");

        LocalDate validFrom = LocalDate.of(2026, 7, 21);
        RetrievalBundleCompilerCli.compile(portfolio, model, output, validFrom);

        assertThat(output).isDirectoryContaining(path ->
                path.getFileName().toString().equals("rag-documents.jsonl"));
        assertThat(output).isDirectoryContaining(path ->
                path.getFileName().toString().equals("keyword-index.json"));
        assertThat(output).isDirectoryContaining(path ->
                path.getFileName().toString().equals("vector-index.bin"));
        assertThat(output).isDirectoryContaining(path ->
                path.getFileName().toString().equals("retrieval-manifest.json"));
        assertThatThrownBy(() -> RetrievalBundleCompilerCli.compile(
                portfolio, model, output, validFrom))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalDocumentCompilerMatchesTheFullCompilerAndNeverOverwrites()
            throws Exception {
        Path root = projectRoot();
        Path model = root.resolve("runtime-models/bge-small-zh-v1.5");
        Assumptions.assumeTrue(Files.isRegularFile(
                model.resolve("onnx/model_quantized.onnx")));
        Path portfolio = root.resolve(
                "backend/src/main/resources/public-data/bundle/portfolio.json");
        Path documents = temporary.resolve("rag-documents.jsonl");
        Path artifacts = temporary.resolve("full-artifacts");
        LocalDate validFrom = LocalDate.of(2026, 7, 21);

        RagDocumentCompilerCli.compile(portfolio, documents, validFrom);
        RetrievalBundleCompilerCli.compile(portfolio, model, artifacts, validFrom);

        assertThat(Files.readAllBytes(documents)).isEqualTo(
                Files.readAllBytes(artifacts.resolve("rag-documents.jsonl")));
        assertThatThrownBy(() -> RagDocumentCompilerCli.compile(
                portfolio, documents, validFrom))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("backend"))
                ? current
                : current.getParent();
    }
}
