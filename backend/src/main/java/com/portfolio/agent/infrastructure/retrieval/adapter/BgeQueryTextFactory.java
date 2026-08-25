package com.portfolio.agent.infrastructure.retrieval.adapter;

/**
 * BGE 查询文本工厂：为检索查询拼接 BGE 模型要求的查询指令前缀。
 *
 * <p>BGE 系列模型约定 query 侧需要在原文前拼接固定 instruction 才能获得
 * 正确的 embedding（文档侧不拼接）。指令在构造期校验非空并冻结。
 */
public final class BgeQueryTextFactory {

    private final String instruction;

    /**
     * 构造工厂。
     *
     * @param instruction BGE 查询指令前缀，非空白文本
     * @throws IllegalArgumentException 指令为 null 或空白时抛出
     */
    public BgeQueryTextFactory(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("query instruction is required");
        }
        this.instruction = instruction;
    }

    /**
     * 拼接指令前缀与查询文本。
     *
     * @param localQueryText 原始本地查询文本，非空白
     * @return 形如 {@code instruction + localQueryText} 的模型输入
     * @throws IllegalArgumentException 查询文本为 null 或空白时抛出
     */
    public String prepare(String localQueryText) {
        if (localQueryText == null || localQueryText.isBlank()) {
            throw new IllegalArgumentException("local query text is required");
        }
        return instruction + localQueryText;
    }
}
