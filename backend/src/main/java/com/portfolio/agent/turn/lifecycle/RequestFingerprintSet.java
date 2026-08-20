package com.portfolio.agent.turn.lifecycle;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 当前写入指纹及一个轮换窗口内可接受的旧指纹。 */
public record RequestFingerprintSet(
        String currentKeyId, byte[] current, List<Candidate> candidates) {
    public RequestFingerprintSet {
        if (currentKeyId == null || currentKeyId.isBlank()) {
            throw new IllegalArgumentException("current fingerprint key id is required");
        }
        current = requireFingerprint(current);
        ArrayList<Candidate> copied = new ArrayList<>();
        copied.add(new Candidate(currentKeyId, current));
        Objects.requireNonNull(candidates, "candidates").forEach(value -> {
            Candidate candidate = Objects.requireNonNull(value, "candidate");
            if (copied.stream().noneMatch(existing ->
                    existing.keyId().equals(candidate.keyId()))) {
                copied.add(candidate);
            }
        });
        candidates = List.copyOf(copied);
    }

    public RequestFingerprintSet(byte[] current, List<byte[]> previousFingerprints) {
        this("test-current", current, indexed(previousFingerprints));
    }

    public static RequestFingerprintSet single(byte[] fingerprint) {
        return new RequestFingerprintSet("test-current", fingerprint, List.of());
    }

    public boolean matches(byte[] stored) {
        return stored != null && candidates.stream()
                .anyMatch(candidate -> MessageDigest.isEqual(candidate.fingerprint(), stored));
    }

    @Override public byte[] current() { return current.clone(); }
    @Override public List<Candidate> candidates() {
        return List.copyOf(candidates);
    }

    private static List<Candidate> indexed(List<byte[]> values) {
        java.util.ArrayList<Candidate> candidates = new java.util.ArrayList<>();
        int index = 0;
        for (byte[] value : values) {
            candidates.add(new Candidate("test-previous-" + index++, value));
        }
        return List.copyOf(candidates);
    }

    private static byte[] requireFingerprint(byte[] value) {
        byte[] copied = Objects.requireNonNull(value, "fingerprint").clone();
        if (copied.length < 1 || copied.length > 64) {
            throw new IllegalArgumentException("fingerprint length is invalid");
        }
        return copied;
    }

    public record Candidate(String keyId, byte[] fingerprint) {
        public Candidate {
            if (keyId == null || keyId.isBlank()) {
                throw new IllegalArgumentException("fingerprint key id is required");
            }
            fingerprint = requireFingerprint(fingerprint);
        }
        @Override public byte[] fingerprint() { return fingerprint.clone(); }
    }
}
