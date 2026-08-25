package com.portfolio.agent.infrastructure.retrieval.adapter;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.portfolio.agent.infrastructure.retrieval.EmbeddingVector;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ONNX 本地 embedding 适配器：在进程内用 ONNX Runtime + HuggingFace
 * tokenizer 运行量化 BGE 模型，实现 {@link LocalEmbeddingPort}。
 *
 * <p>本地公开检索的 BGE 通道执行体：不发起任何外部网络调用，query 侧
 * 按约定拼接 BGE 查询指令（文档侧由 {@link #forDocuments} 创建时不拼接）。
 * 初始化与推理失败都收敛为封闭 code 的
 * {@link LocalEmbeddingFailureException}（模型/分词器缺失、初始化失败、
 * 推理失败、形状或维度非法），不泄漏文本内容或内部路径。
 * 实例有状态（会话与分词器），方法级同步保证单进程内线程安全；
 * 用完必须 {@link #close()} 释放原生资源。
 */
public final class OnnxLocalEmbeddingAdapter implements LocalEmbeddingPort, AutoCloseable {

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final BgeQueryTextFactory queryTextFactory;
    private final EmbeddingPostProcessor postProcessor;
    private final int dimension;

    /**
     * 查询侧构造：文本先经 BGE 查询指令前缀处理再进入模型。
     *
     * @param modelDirectory 已通过工件校验的本地模型目录
     * @param queryInstruction BGE 查询指令前缀
     * @param maxTokens 分词截断长度上限
     * @param dimension 期望的输出向量维度（推理后校验）
     * @param intraOpThreads / interOpThreads ONNX 算子内/间并行线程数
     * @throws LocalEmbeddingFailureException 模型或分词器文件缺失、
     *         初始化失败（LOCAL_MODEL_INITIALIZATION_FAILED）
     */
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

    /**
     * 文档侧构造：治理导入专用，不做查询指令前缀处理。
     * 其余参数语义与查询侧构造一致。
     */
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

    /**
     * 全参构造：解析模型与分词器文件、创建 ONNX 会话（顺序执行、全量优化、
     * 错误级日志）并构建带截断的分词器；查询指令仅在查询侧启用。
     * 任何初始化异常都收敛为 LOCAL_MODEL_INITIALIZATION_FAILED。
     */
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

    /**
     * 对单段文本执行本地 embedding 推理（同步、可能耗时）。
     *
     * <p>流程：按侧别预处理文本 → 分词（自动截断到 maxTokens）→ 组装
     * input_ids/attention_mask（按需 token_type_ids）张量 → ONNX 会话推理
     * → 取 last_hidden_state 做均值池化与归一化 → 校验维度与预期一致。
     *
     * @param localQueryText 查询侧为原始查询文本；文档侧为文档正文
     * @return 归一化后的 embedding 向量
     * @throws LocalEmbeddingFailureException 文本缺失（DOCUMENT_TEXT_REQUIRED）、
     *         推理失败（LOCAL_INFERENCE_FAILED）、输出形状或维度非法
     */
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
                // 部分导出的 BGE 模型不带 token_type_ids 输入，按会话声明按需提供。
                if (session.getInputNames().contains("token_type_ids")) {
                    inputs.put("token_type_ids", typeTensor);
                }
                try (OrtSession.Result result = session.run(inputs)) {
                    // 优先按名称取 last_hidden_state，旧版导出则退回首输出。
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

    /** 校验文档侧文本非空白（文档模式没有查询指令兜底拼接）。 */
    private String requireDocumentText(String text) {
        if (text == null || text.isBlank()) {
            throw new LocalEmbeddingFailureException("DOCUMENT_TEXT_REQUIRED");
        }
        return text;
    }

    /**
     * 定位量化 ONNX 模型文件：优先 {@code onnx/model_quantized.onnx}
     * 嵌套布局，兼容目录根部的平铺布局，两者皆缺则失败。
     */
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

    /** 释放分词器与 ONNX 会话等原生资源。 */
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
