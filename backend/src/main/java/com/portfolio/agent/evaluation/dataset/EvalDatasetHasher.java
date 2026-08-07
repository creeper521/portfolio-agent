package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.domain.EvalCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stable dataset identity over the real content of the evaluation dataset:
 * the exact bytes of every handwritten case file plus the canonical JSON of
 * every generated (expanded) smoke case. Handwritten file order does not
 * matter; any content change invalidates the hash.
 */
public final class EvalDatasetHasher {

    private final ObjectMapper mapper;

    public EvalDatasetHasher(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public String hash(List<Path> handwrittenFiles, List<EvalCase> generatedCases) {
        Objects.requireNonNull(handwrittenFiles, "handwrittenFiles");
        Objects.requireNonNull(generatedCases, "generatedCases");
        MessageDigest digest = sha256();
        List<Path> ordered = new ArrayList<>(handwrittenFiles);
        ordered.sort(Comparator.comparing(Path::toString));
        for (Path file : ordered) {
            digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try {
                digest.update(Files.readAllBytes(file));
            } catch (IOException failure) {
                throw new IllegalArgumentException(
                        "unable to read dataset file: " + file, failure);
            }
            digest.update((byte) 0);
        }
        List<EvalCase> orderedCases = new ArrayList<>(generatedCases);
        orderedCases.sort(Comparator.comparing(EvalCase::getId));
        for (EvalCase evalCase : orderedCases) {
            try {
                byte[] json = mapper.writeValueAsBytes(evalCase);
                digest.update(json);
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException(
                        "unable to serialize generated case " + evalCase.getId(), failure);
            }
            digest.update((byte) 0);
        }
        return toHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(Character.forDigit((value >> 4) & 0xF, 16));
            hex.append(Character.forDigit(value & 0xF, 16));
        }
        return hex.toString();
    }
}
