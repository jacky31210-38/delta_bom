package com.delta.bom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BomComponentUpdateRequest {

    @NotNull(message = "數量不可為空")
    @DecimalMin(value = "0.0001", message = "數量必須大於 0")
    private BigDecimal quantity;
}
