package com.portfolio.agent.ingestion.service;

import com.portfolio.agent.ingestion.domain.MarkdownChunk;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class MarkdownChunker {

    public List<MarkdownChunk> chunk(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("markdown is required");
        }
        List<MarkdownChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (line.startsWith("#") && current.toString().strip().length() > 0) {
                append(chunks, current);
            }
            if (line.isBlank() && current.toString().strip().length() > 0) {
                append(chunks, current);
                continue;
            }
            if (!line.isBlank() || current.length() > 0) {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (current.toString().strip().length() > 0) {
            append(chunks, current);
        }
        return List.copyOf(chunks);
    }

    private void append(List<MarkdownChunk> chunks, StringBuilder current) {
        String text = current.toString().strip();
        current.setLength(0);
        if (!text.isEmpty()) {
            chunks.add(new MarkdownChunk(chunks.size(), text, sha256(text)));
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
