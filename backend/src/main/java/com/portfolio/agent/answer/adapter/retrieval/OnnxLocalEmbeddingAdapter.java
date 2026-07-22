package com.portfolio.agent.answer.adapter.retrieval;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OnnxLocalEmbeddingAdapter implements LocalEmbeddingPort, AutoCloseable {

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final BgeQueryTextFactory queryTextFactory;
    private final EmbeddingPostProcessor postProcessor;
    private final int dimension;

    public OnnxLocalEmbeddingAdapter(
            Path modelDirectory,
            String queryInstruction,
            int maxTokens,
            int dimension,
            int intraOpThreads,
            int interOpThreads
    ) {
        this(modelDirectory, queryInstruction, maxTokens, dimension,
                intraOpThreads, interOpThreads, true);
    }

    public static OnnxLocalEmbeddingAdapter forDocuments(
            Path modelDirectory,
            int maxTokens,
            int dimension,
            int intraOpThreads,
            int interOpThreads
    ) {
        return new OnnxLocalEmbeddingAdapter(
                modelDirectory, null, maxTokens, dimension,
                intraOpThreads, interOpThreads, false);
    }

    private OnnxLocalEmbeddingAdapter(
            Path modelDirectory,
            String queryInstruction,
            int maxTokens,
            int dimension,
            int intraOpThreads,
            int interOpThreads,
            boolean prependQueryInstruction
    ) {
        try {
            Path modelPath = resolveModel(modelDirectory);
            Path tokenizerPath = modelDirectory.resolve("tokenizer.json");
            if (!Files.isRegularFile(tokenizerPath)) {
                throw new LocalEmbeddingFailureException("TOKENIZER_FILE_MISSING");
            }
            environment = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(intraOpThreads);
                options.setInterOpNumThreads(interOpThreads);
                options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                options.setSessionLogLevel(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
                session = environment.createSession(modelPath.toString(), options);
            }
            tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optAddSpecialTokens(true)
                    .optTruncation(true)
                    .optMaxLength(maxTokens)
                    .build();
            queryTextFactory = prependQueryInstruction
                    ? new BgeQueryTextFactory(queryInstruction)
                    : null;
            postProcessor = new EmbeddingPostProcessor();
            this.dimension = dimension;
        } catch (LocalEmbeddingFailureException exception) {
            throw exception;
        } catch (IOException | OrtException | RuntimeException exception) {
            throw new LocalEmbeddingFailureException("LOCAL_MODEL_INITIALIZATION_FAILED");
        }
    }

    @Override
    public synchronized EmbeddingVector embedQuery(String localQueryText) {
        try {
            String modelText = queryTextFactory == null
                    ? requireDocumentText(localQueryText)
                    : queryTextFactory.prepare(localQueryText);
            Encoding encoding = tokenizer.encode(modelText);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] typeIds = encoding.getTypeIds();
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                            environment, new long[][]{inputIds});
                    OnnxTensor maskTensor = OnnxTensor.createTensor(
                            environment, new long[][]{attentionMask});
                    OnnxTensor typeTensor = OnnxTensor.createTensor(
                            environment, new long[][]{typeIds})) {
                Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
                inputs.put("input_ids", inputTensor);
                inputs.put("attention_mask", maskTensor);
                if (session.getInputNames().contains("token_type_ids")) {
                    inputs.put("token_type_ids", typeTensor);
                }
                try (OrtSession.Result result = session.run(inputs)) {
                    OnnxValue output = result.get("last_hidden_state")
                            .orElseGet(() -> result.get(0));
                    Object value = output.getValue();
                    if (!(value instanceof float[][][] hiddenBatch)
                            || hiddenBatch.length != 1) {
                        throw new LocalEmbeddingFailureException("MODEL_OUTPUT_SHAPE_INVALID");
                    }
                    EmbeddingVector vector = postProcessor.meanPoolAndNormalize(
                            hiddenBatch[0], attentionMask);
                    if (vector.dimension() != dimension) {
                        throw new LocalEmbeddingFailureException("MODEL_OUTPUT_DIMENSION_INVALID");
                    }
                    return vector;
                }
            }
        } catch (LocalEmbeddingFailureException exception) {
            throw exception;
        } catch (OrtException | RuntimeException exception) {
            throw new LocalEmbeddingFailureException("LOCAL_INFERENCE_FAILED");
        }
    }

    private String requireDocumentText(String text) {
        if (text == null || text.isBlank()) {
            throw new LocalEmbeddingFailureException("DOCUMENT_TEXT_REQUIRED");
        }
        return text;
    }

    private Path resolveModel(Path modelDirectory) {
        Path nested = modelDirectory.resolve("onnx").resolve("model_quantized.onnx");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        Path flat = modelDirectory.resolve("model_quantized.onnx");
        if (Files.isRegularFile(flat)) {
            return flat;
        }
        throw new LocalEmbeddingFailureException("MODEL_FILE_MISSING");
    }

    @Override
    public void close() {
        tokenizer.close();
        try {
            session.close();
        } catch (OrtException exception) {
            throw new LocalEmbeddingFailureException("LOCAL_MODEL_CLOSE_FAILED");
        }
    }
}
