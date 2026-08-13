package com.portfolio.agent.answer.context.adapter.memory;

/** No deterministic non-active Context can be pruned without deleting an Active slot. */
public final class ContextCapacityExceededException extends IllegalStateException {
    public ContextCapacityExceededException() {
        super("conversation Context capacity is exhausted");
    }
}
