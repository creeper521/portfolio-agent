package com.portfolio.agent.ingestion.domain;

import java.util.List;

public final class MarkdownScanReport {

    private final List<MarkdownScanEntry> entries;

    public MarkdownScanReport(List<MarkdownScanEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<MarkdownScanEntry> getEntries() {
        return entries;
    }
}
