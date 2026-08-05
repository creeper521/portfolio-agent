package com.portfolio.agent.portfolio.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class PresetContractVersion {

    private PresetContractVersion() {
    }

    public static String calculate(
            String id,
            String text,
            List<String> aliases,
            String contractSubjectId,
            List<String> requiredClaimIds,
            List<String> supportingClaimIds,
            QuestionEvidenceRequirement evidenceRequirement,
            PresetContractStatus contractStatus
    ) {
        String canonical = "id=" + normalize(id) + "\n"
                + "text=" + normalize(text) + "\n"
                + "aliases=" + String.join(",", normalized(aliases)) + "\n"
                + "subject=" + normalize(contractSubjectId) + "\n"
                + "requiredClaimIds=" + String.join(",", normalized(requiredClaimIds)) + "\n"
                + "supportingClaimIds=" + String.join(",", normalized(supportingClaimIds)) + "\n"
                + "minimumApprovedEvidencePerRequiredClaim="
                + evidenceRequirement.getMinimumApprovedEvidencePerRequiredClaim() + "\n"
                + "publicOnly=" + evidenceRequirement.isPublicOnly() + "\n"
                + "status=" + contractStatus.name().toLowerCase(Locale.ROOT) + "\n";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "pcv1-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static List<String> normalized(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(normalize(value));
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip()
                .replaceAll("\\s+", " ");
    }
}
