package com.portfolio.agent.answer.adapter.retrieval;

public final class BgeQueryTextFactory {

    private final String instruction;

    public BgeQueryTextFactory(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("query instruction is required");
        }
        this.instruction = instruction;
    }

    public String prepare(String localQueryText) {
        if (localQueryText == null || localQueryText.isBlank()) {
            throw new IllegalArgumentException("local query text is required");
        }
        return instruction + localQueryText;
    }
}
