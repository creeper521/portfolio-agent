package com.portfolio.agent.answer.service;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AnswerAdmission implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable release;

    AnswerAdmission(Runnable release) {
        this.release = release;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release.run();
        }
    }
}
