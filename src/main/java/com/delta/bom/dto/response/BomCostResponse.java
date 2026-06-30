package com.delta.bom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BomCostResponse {

    private String rootCode;
    private String rootName;
    private BigDecimal totalCost;
    private List<CostDetailItem> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostDetailItem {
        private String itemCode;           // 主料編碼
        private String itemName;           // 主料名稱
        private String effectiveItemCode;  // 實際使用料編碼（可能為替代料）
        private String effectiveItemName;  // 實際使用料名稱
        private BigDecimal bomQuantity;    // 此節點在 BOM 中的數量（未乘父層）
        private BigDecimal effectiveQty;   // 有效數量（已乘所有父層數量）
        private BigDecimal unitPrice;      // 加權後有效單價
        private BigDecimal subtotal;       // 小計
        private boolean substituted;       // 是否使用替代料
    }
}
