package com.portfolio.agent.turn.lifecycle;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 当前写入指纹及一个轮换窗口内可接受的旧指纹。 */
public record RequestFingerprintSet(
        String currentKeyId, byte[] current, List<Candidate> candidates) {
    /**
     * 紧凑构造器：复制并校验指纹，把当前指纹并入候选集并对 candidates 按 keyId 去重，
     * 保证 {@link #matches(byte[])} 覆盖当前密钥与全部旧密钥各一份。
     */
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

    /** 测试便捷构造器：previousFingerprints 按序号自动生成 keyId。 */
    public RequestFingerprintSet(byte[] current, List<byte[]> previousFingerprints) {
        this("test-current", current, indexed(previousFingerprints));
    }

    /** 测试便捷工厂：只包含当前指纹、无轮换候选。 */
    public static RequestFingerprintSet single(byte[] fingerprint) {
        return new RequestFingerprintSet("test-current", fingerprint, List.of());
    }

    /**
     * 判断已存储指纹是否命中当前或任一轮换候选。
     *
     * <p>使用 {@link MessageDigest#isEqual} 做恒定时间比较，避免指纹内容通过比较耗时泄露。</p>
     */
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

    /** 一个密钥世代（keyId）对应的请求指纹副本。 */
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
