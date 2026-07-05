package com.delta.bom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BomComponentCreateRequest {

    /** 父物料編碼（BOM 裡包含子物料的那一方） */
    @NotBlank(message = "父物料編碼不可為空")
    private String parentMaterialCode;

    /** 子物料編碼（被包含的那一方） */
    @NotBlank(message = "子物料編碼不可為空")
    private String childMaterialCode;

    /** 父物料的 BOM 裡，這個子物料的用量 */
    @NotNull(message = "數量不可為空")
    @DecimalMin(value = "0.0001", message = "數量必須大於 0")
    private BigDecimal quantity;
}
