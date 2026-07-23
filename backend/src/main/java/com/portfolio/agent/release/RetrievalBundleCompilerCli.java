package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.agent.answer.adapter.retrieval.OnnxLocalEmbeddingAdapter;
import com.portfolio.agent.answer.adapter.retrieval.LocalEmbeddingArtifact;
import com.portfolio.agent.answer.adapter.retrieval.LocalEmbeddingArtifactVerifier;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.release.RetrievalBundleCompiler;
import com.portfolio.agent.portfolio.release.RetrievalCompilation;
import com.portfolio.agent.portfolio.repository.file.PortfolioSnapshotJsonReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class RetrievalBundleCompilerCli {

    private RetrievalBundleCompilerCli() {
    }

    public static void main(String[] args) {
        try {
            Map<String, String> options = options(args);
            compile(
                    Path.of(required(options, "--portfolio")),
                    Path.of(required(options, "--model-dir")),
                    Path.of(required(options, "--output-dir")),
                    LocalDate.parse(required(options, "--valid-from")));
            System.out.println("Retrieval bundle artifacts compiled.");
        } catch (RuntimeException | IOException exception) {
            System.err.println("RETRIEVAL_COMPILE_FAILED");
            System.exit(1);
        }
    }

    static void compile(
            Path portfolioFile,
            Path modelDirectory,
            Path outputDirectory,
            LocalDate validFrom
    )
            throws IOException {
        if (!Files.isRegularFile(portfolioFile) || !Files.isDirectory(modelDirectory)
                || Files.exists(outputDirectory)) {
            throw new IllegalArgumentException("retrieval compiler input is invalid");
        }
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        PortfolioSnapshot snapshot = new PortfolioSnapshotJsonReader(mapper).readBundle(
                Files.readAllBytes(portfolioFile));
        RetrievalCompilation compilation;
        LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                .verify(modelDirectory);
        try (OnnxLocalEmbeddingAdapter adapter = OnnxLocalEmbeddingAdapter.forDocuments(
                modelDirectory, artifact.getMaxTokens(), artifact.getDimension(),
                artifact.getIntraOpThreads(), artifact.getInterOpThreads())) {
            compilation = new RetrievalBundleCompiler(
                    text -> adapter.embedQuery(text).copyValues(),
                    artifact.getDescriptorSha256(), artifact.getDimension())
                    .compile(snapshot, validFrom);
        }

        Path absoluteOutput = outputDirectory.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("retrieval output parent is invalid");
        }
        Path temporary = Files.createTempDirectory(parent, ".retrieval-compile-");
        boolean moved = false;
        try {
            Files.write(temporary.resolve("rag-documents.jsonl"),
                    compilation.getRagDocuments());
            Files.write(temporary.resolve("keyword-index.json"),
                    compilation.getKeywordIndex());
            Files.write(temporary.resolve("vector-index.bin"),
                    compilation.getVectorIndex());
            Files.write(temporary.resolve("retrieval-manifest.json"),
                    mapper.writeValueAsBytes(compilation.getManifest()));
            Files.move(temporary, absoluteOutput, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } finally {
            if (!moved && Files.exists(temporary)) {
                try (java.util.stream.Stream<Path> paths = Files.walk(temporary)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }

    static String descriptorHash() throws IOException {
        return new LocalEmbeddingArtifactVerifier().descriptor().getDescriptorSha256();
    }

    private static Map<String, String> options(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("compiler options are invalid");
        }
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (!args[index].startsWith("--")
                    || options.put(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("compiler options are invalid");
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("required compiler option is missing");
        }
        return value;
    }
}
