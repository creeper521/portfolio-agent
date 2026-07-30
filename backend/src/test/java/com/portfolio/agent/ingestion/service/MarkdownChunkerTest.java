package com.portfolio.agent.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.ingestion.domain.MarkdownChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {

    @Test
    void splitsHeadingsAndBlankLinesIntoStableNonEmptyChunks() {
        List<MarkdownChunk> chunks = new MarkdownChunker().chunk("\n# First\nalpha\n\n\n## Second\n beta \n\n");

        assertThat(chunks)
                .extracting(MarkdownChunk::getOrdinal, MarkdownChunk::getText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "# First\nalpha"),
                        org.assertj.core.groups.Tuple.tuple(1, "## Second\n beta"));
        assertThat(chunks.get(0).getHash()).isEqualTo("94e01b5e09928e533449e20ddc489cf63f9709c919dda6a19cf087c0d7e965f3");
    }
}
