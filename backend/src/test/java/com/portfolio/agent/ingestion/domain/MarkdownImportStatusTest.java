package com.portfolio.agent.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownImportStatusTest {

    @Test
    void exposesPartialImportStateAndTypedVectorAndRevisionStatuses() {
        MarkdownImportReport report = new MarkdownImportReport(1, 0, 0, 0, 0, 0, 1);
        ImportedMarkdownChunk chunk = new ImportedMarkdownChunk(
                0, "hash", "private", null, MarkdownVectorStatus.VECTOR_PENDING);
        ImportedMarkdownDocument document = new ImportedMarkdownDocument(
                "note.md", "hash", 7, List.of(chunk), MarkdownRevisionStatus.VECTOR_PENDING);

        assertThat(report.getVectorPending()).isEqualTo(1);
        assertThat(report.isPartial()).isTrue();
        assertThat(chunk.getVectorStatus()).isEqualTo(MarkdownVectorStatus.VECTOR_PENDING);
        assertThat(document.getRevisionStatus()).isEqualTo(MarkdownRevisionStatus.VECTOR_PENDING);
        assertThat(document.isReplaceCurrentRevision()).isFalse();
    }

    @Test
    void rejectsParsedRevisionWithPendingChunk() {
        ImportedMarkdownChunk pending = new ImportedMarkdownChunk(
                0, "hash", "private", null, MarkdownVectorStatus.VECTOR_PENDING);

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() ->
                new ImportedMarkdownDocument(
                        "note.md", "hash", 7, List.of(pending), MarkdownRevisionStatus.PARSED));
    }

    @Test
    void enforcesVectorContractOnChunkConstruction() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new ImportedMarkdownChunk(
                0, "hash", "private", new float[511], MarkdownVectorStatus.READY));
        float[] nonFinite = new float[512];
        nonFinite[0] = Float.NaN;
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new ImportedMarkdownChunk(
                0, "hash", "private", nonFinite, MarkdownVectorStatus.READY));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new ImportedMarkdownChunk(
                0, "hash", "private", new float[512], MarkdownVectorStatus.VECTOR_PENDING));
    }
}
