package com.delta.bom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstituteInfoResponse {

    private String scenarioKey;
    private String substituteCode;
    private String substituteName;
    private BigDecimal substituteQty;    // 覆蓋主料 BOM 需求量中的多少數量
    private BigDecimal substituteRatio;  // 替代比例：1 顆主料對應幾顆替代料
    private BigDecimal unitPrice;
    private String reason;
}
