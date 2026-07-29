package com.portfolio.agent.common.observability;

import java.util.Objects;

public final class SafeExceptionRenderer {

    private static final String FIRST_PARTY_PACKAGE_PREFIX = "com.portfolio.agent.";
    private static final int MAX_RENDERED_FRAMES = 20;

    public String render(Throwable exception) {
        Throwable requiredException = Objects.requireNonNull(
                exception, "exception must not be null");
        StringBuilder rendered = new StringBuilder(
                requiredException.getClass().getName());
        int renderedFrameCount = 0;
        for (StackTraceElement frame : requiredException.getStackTrace()) {
            if (!frame.getClassName().startsWith(FIRST_PARTY_PACKAGE_PREFIX)) {
                continue;
            }
            if (renderedFrameCount == MAX_RENDERED_FRAMES) {
                break;
            }
            rendered.append(System.lineSeparator())
                    .append("at ")
                    .append(frame.getClassName())
                    .append('.')
                    .append(frame.getMethodName())
                    .append('(')
                    .append(safeFileName(frame.getFileName()))
                    .append(':')
                    .append(frame.getLineNumber())
                    .append(')');
            renderedFrameCount++;
        }
        return rendered.toString();
    }

    private String safeFileName(String fileName) {
        if (fileName == null) {
            return "Unknown Source";
        }
        int lastSeparator = Math.max(
                fileName.lastIndexOf('/'),
                fileName.lastIndexOf('\\'));
        return fileName.substring(lastSeparator + 1);
    }
}
