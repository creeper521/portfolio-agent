package com.portfolio.agent.answer.service;

import com.portfolio.agent.common.text.RetrievalTextNormalizer;

import java.text.Normalizer;
import java.util.Locale;

public final class RetrievalQueryNormalizer {

    private final RetrievalTextNormalizer termNormalizer = new RetrievalTextNormalizer();

    public NormalizedRetrievalQuery normalize(String source) {
        if (source == null) {
            throw new IllegalArgumentException("local query text is required");
        }
        String localText = Normalizer.normalize(source, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip()
                .replaceAll("\\s+", " ");
        return new NormalizedRetrievalQuery(localText, termNormalizer.terms(localText));
    }
}
