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
        private String effectiveItemCode;  // 此行實際使用料編碼（可能為替代料，或主料本身）
        private String effectiveItemName;  // 此行實際使用料名稱
        private String scenarioKey;        // 此行替代來自哪個方案（非替代行為 null）
        private BigDecimal bomQuantity;    // 此行覆蓋的 BOM 數量（未乘父層；一顆主料可能拆成多行）
        private BigDecimal effectiveQty;   // 有效數量（已乘父層數量與替代比例）
        private BigDecimal unitPrice;      // 此行單價
        private BigDecimal subtotal;       // 小計
        private boolean substituted;       // 是否使用替代料
    }
}
