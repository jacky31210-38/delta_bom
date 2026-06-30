package com.delta.bom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubstituteRequest {

    @NotBlank(message = "主料編碼不可為空")
    private String primaryMaterialCode;

    @NotBlank(message = "替代料編碼不可為空")
    private String substituteMaterialCode;

    private String substituteMaterialName;

    private String reason;

    @NotNull(message = "替代數量不可為空")
    @DecimalMin(value = "0.0001", message = "替代數量必須大於 0")
    private BigDecimal substituteQty;

    @NotNull(message = "替代料單價不可為空")
    @DecimalMin(value = "0.0", inclusive = false, message = "替代料單價必須大於 0")
    private BigDecimal unitPrice;
}
