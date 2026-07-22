package com.portfolio.agent.answer.service;

import java.util.List;

public final class NormalizedRetrievalQuery {

    private final String localText;
    private final List<String> terms;

    public NormalizedRetrievalQuery(String localText, List<String> terms) {
        this.localText = localText;
        this.terms = List.copyOf(terms);
    }

    public String getLocalText() { return localText; }
    public List<String> getTerms() { return terms; }
}
