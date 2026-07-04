package com.delta.bom.exception;

public class ScenarioNotFoundException extends RuntimeException {

    public ScenarioNotFoundException(String scenarioKey) {
        super("查無替代方案：" + scenarioKey);
    }
}
