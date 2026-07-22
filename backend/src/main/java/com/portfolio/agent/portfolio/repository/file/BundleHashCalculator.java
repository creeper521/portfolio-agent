package com.portfolio.agent.portfolio.repository.file;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

public final class BundleHashCalculator {

    private BundleHashCalculator() {
    }

    public static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String candidatePayloadHash(Map<String, byte[]> files) {
        if (files.containsKey("rag-documents.jsonl")) {
            return sha256(frame(
                    "portfolio.json", required(files, "portfolio.json"),
                    "presentation.json", required(files, "presentation.json"),
                    "rag-documents.jsonl", required(files, "rag-documents.jsonl")
            ));
        }
        return sha256(frame(
                "portfolio.json", required(files, "portfolio.json"),
                "presentation.json", required(files, "presentation.json")
        ));
    }

    public static String runtimeBundleHash(byte[] manifest, byte[] checksums) {
        return sha256(frame(
                "manifestHash", sha256(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "checksumsHash", sha256(checksums).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ));
    }

    private static byte[] required(Map<String, byte[]> files, String name) {
        byte[] bytes = files.get(name);
        if (bytes == null) {
            throw new IllegalArgumentException("missing bundle file: " + name);
        }
        return bytes;
    }

    private static byte[] frame(Object... entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(output);
            for (int index = 0; index < entries.length; index += 2) {
                byte[] name = ((String) entries[index])
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] content = (byte[]) entries[index + 1];
                data.writeInt(name.length);
                data.write(name);
                data.writeLong(content.length);
                data.write(content);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("unable to frame bundle hash input", exception);
        }
    }
}
