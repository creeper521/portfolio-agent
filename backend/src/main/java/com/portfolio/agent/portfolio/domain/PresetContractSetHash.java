package com.portfolio.agent.portfolio.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public final class PresetContractSetHash {

    private PresetContractSetHash() {
    }

    public static String calculate(List<QuestionDefinition> activeQuestions) {
        List<QuestionDefinition> active = List.copyOf(activeQuestions);
        for (QuestionDefinition question : active) {
            if (!question.isActiveContract()) {
                throw new IllegalArgumentException(
                        "non-active preset in contract set: " + question.getId());
            }
            String id = question.getId();
            String version = question.getContractVersion();
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("active preset id is empty");
            }
            if (version == null || version.isEmpty()) {
                throw new IllegalArgumentException(
                        "active preset contract version is empty: " + id);
            }
        }
        long distinctIds = active.stream()
                .map(QuestionDefinition::getId)
                .distinct()
                .count();
        if (distinctIds != active.size()) {
            throw new IllegalArgumentException("duplicate active preset id");
        }
        String canonical = active.stream()
                .sorted(Comparator.comparing(QuestionDefinition::getId))
                .map(question -> "{\"presetId\":\"" + question.getId()
                        + "\",\"contractVersion\":\"" + question.getContractVersion() + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
        return "sha256:" + sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
