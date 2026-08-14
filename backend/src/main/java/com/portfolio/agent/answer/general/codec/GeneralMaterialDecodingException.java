package com.portfolio.agent.answer.general.codec;

public final class GeneralMaterialDecodingException extends RuntimeException {
    public GeneralMaterialDecodingException() { super("invalid general material draft"); }
    public GeneralMaterialDecodingException(Throwable cause) { super("invalid general material draft", cause); }
}
