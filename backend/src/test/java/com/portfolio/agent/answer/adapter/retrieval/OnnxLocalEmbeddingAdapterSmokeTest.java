package com.portfolio.agent.answer.adapter.retrieval;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OnnxLocalEmbeddingAdapterSmokeTest {

    @Test
    void embedsAQueryLocallyIntoAUnitLength512Vector() {
        Path modelDirectory = Path.of(System.getProperty(
                "portfolio.embedding.modelDir", "C:/tmp/portfolio-bge-audit"));
        Assumptions.assumeTrue(Files.isRegularFile(
                        modelDirectory.resolve("model_quantized.onnx"))
                || Files.isRegularFile(modelDirectory.resolve("onnx")
                        .resolve("model_quantized.onnx")));

        try (OnnxLocalEmbeddingAdapter adapter = new OnnxLocalEmbeddingAdapter(
                modelDirectory, "为这个句子生成表示以用于检索相关文章：",
                256, 512, 2, 1)) {
            EmbeddingVector vector = adapter.embedQuery("SQL 审计项目已经完成交付");

            assertThat(vector.dimension()).isEqualTo(512);
            double squaredNorm = 0.0;
            for (float value : vector.copyValues()) {
                squaredNorm += value * value;
            }
            assertThat(Math.sqrt(squaredNorm)).isCloseTo(
                    1.0, org.assertj.core.data.Offset.offset(0.001));
        }
    }
}
