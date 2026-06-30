package com.delta.bom.exception;

public class BomNotFoundException extends RuntimeException {

    public BomNotFoundException(String code) {
        super("查無物料或BOM：" + code);
    }
}
