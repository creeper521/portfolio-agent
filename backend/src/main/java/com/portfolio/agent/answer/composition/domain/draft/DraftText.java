package com.portfolio.agent.answer.composition.domain.draft;

import java.util.List;

public final class DraftText {
    private final String text;
    private final List<String> supports;
    public DraftText(String text, List<String> supports) {
        this.text = DraftValues.text(text);
        this.supports = DraftValues.supports(supports);
    }
    public String getText() { return text; }
    public List<String> getSupports() { return supports; }
}
