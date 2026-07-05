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

    /** 查詢的根節點物料編碼 */
    private String rootCode;

    /** 根節點物料名稱 */
    private String rootName;

    /** 整棵 BOM 樹的總成本 */
    private BigDecimal totalCost;

    /** 每一筆計價明細（一顆主料可能因跨方案套用而拆成多行） */
    private List<CostDetailItem> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostDetailItem {
        /** 主料編碼 */
        private String itemCode;
        /** 主料名稱 */
        private String itemName;
        /** 此行實際使用料編碼（可能為替代料，或主料本身） */
        private String effectiveItemCode;
        /** 此行實際使用料名稱 */
        private String effectiveItemName;
        /** 此行替代來自哪個方案（非替代行為 null） */
        private String scenarioKey;
        /** 此行覆蓋的 BOM 數量（未乘父層；一顆主料可能拆成多行） */
        private BigDecimal bomQuantity;
        /** 有效數量（已乘父層數量與替代比例） */
        private BigDecimal effectiveQty;
        /** 此行單價 */
        private BigDecimal unitPrice;
        /** 小計 */
        private BigDecimal subtotal;
        /** 是否使用替代料 */
        private boolean substituted;
    }
}
