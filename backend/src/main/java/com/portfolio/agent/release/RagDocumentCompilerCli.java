package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.release.RetrievalBundleCompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class RagDocumentCompilerCli {
    private RagDocumentCompilerCli() {
    }

    public static void main(String[] args) {
        try {
            Map<String, String> options = options(args);
            compile(Path.of(required(options, "--portfolio")),
                    Path.of(required(options, "--output")),
                    LocalDate.parse(required(options, "--valid-from")));
            System.out.println("Canonical RAG documents compiled.");
        } catch (RuntimeException | IOException exception) {
            System.err.println("RAG_DOCUMENT_COMPILE_FAILED");
            System.exit(1);
        }
    }

    static void compile(Path portfolioFile, Path outputFile, LocalDate validFrom)
            throws IOException {
        if (!Files.isRegularFile(portfolioFile) || Files.exists(outputFile)) {
            throw new IllegalArgumentException("RAG compiler input is invalid");
        }
        Path absoluteOutput = outputFile.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("RAG output parent is invalid");
        }
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PortfolioSnapshot snapshot = mapper.readValue(
                Files.readAllBytes(portfolioFile), PortfolioSnapshot.class);
        byte[] documents = RetrievalBundleCompiler.compileCanonicalDocuments(
                snapshot, validFrom);
        Path temporary = Files.createTempFile(parent, ".rag-documents-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, documents);
            Files.move(temporary, absoluteOutput, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
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
