package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.common.text.RetrievalTextNormalizer;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.repository.file.KeywordIndexFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class KeywordIndexBuilder {

    public static final String FORMAT_VERSION = "keyword-index-v1";

    private final RetrievalTextNormalizer normalizer = new RetrievalTextNormalizer();

    public KeywordIndexFile build(List<RagDocument> documents) {
        List<RagDocument> ordered = documents.stream()
                .sorted(java.util.Comparator.comparing(RagDocument::getChunkId))
                .toList();
        List<KeywordIndexFile.DocumentEntry> entries = new ArrayList<>();
        Map<String, Integer> documentFrequencies = new TreeMap<>();
        int totalLength = 0;
        for (RagDocument document : ordered) {
            List<String> terms = normalizer.terms(document.getText());
            Map<String, Integer> frequencies = new TreeMap<>();
            for (String term : terms) {
                frequencies.merge(term, 1, Integer::sum);
            }
            Set<String> uniqueTerms = new HashSet<>(terms);
            for (String term : uniqueTerms) {
                documentFrequencies.merge(term, 1, Integer::sum);
            }
            totalLength += terms.size();
            entries.add(new KeywordIndexFile.DocumentEntry(
                    document.getChunkId(), terms.size(), frequencies));
        }
        double averageLength = ordered.isEmpty() ? 0.0 : (double) totalLength / ordered.size();
        return new KeywordIndexFile(
                FORMAT_VERSION, RetrievalTextNormalizer.VERSION, ordered.size(),
                averageLength, entries, documentFrequencies);
    }
}
