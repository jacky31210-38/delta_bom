package com.delta.bom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialRequest {

    @NotBlank(message = "物料編碼不可為空")
    private String materialCode;

    @NotBlank(message = "物料名稱不可為空")
    private String materialName;

    private String unit;

    @DecimalMin(value = "0.0", inclusive = false, message = "單價必須大於 0")
    private BigDecimal unitPrice;
}
