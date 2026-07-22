package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.RagDocument;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

public final class RagDocumentHashCalculator {

    private RagDocumentHashCalculator() {
    }

    public static String contentHash(RagDocument document) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(output);
            write(data, document.getChunkId());
            write(data, document.getContentVersion());
            write(data, document.getProjectSlugs());
            write(data, document.getClaimIds());
            write(data, document.getText());
            write(data, document.getTopics());
            write(data, document.getValidFrom());
            write(data, document.getValidUntil());
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(output.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("unable to hash RAG document", exception);
        }
    }

    private static void write(DataOutputStream data, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static void write(DataOutputStream data, List<String> values) throws IOException {
        data.writeInt(values.size());
        for (String value : values) {
            write(data, value);
        }
    }

    private static void write(DataOutputStream data, LocalDate value) throws IOException {
        data.writeBoolean(value != null);
        if (value != null) {
            write(data, value.toString());
        }
    }
}
