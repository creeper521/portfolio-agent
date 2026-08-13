package com.portfolio.agent.answer.composition.domain;

public enum LocaleCode {
    ZH_CN("zh-CN");

    private final String wireValue;
    LocaleCode(String wireValue) { this.wireValue = wireValue; }
    public String getWireValue() { return wireValue; }
    @Override public String toString() { return wireValue; }
}
