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
public class BomNodeResponse {

    /** 物料編碼 */
    private String itemCode;
    /** 物料名稱 */
    private String itemName;
    /** 單位 */
    private String unit;
    /** 在其直接父節點 BOM 裡的數量（邊本身定義的數量，不含祖先層的累乘） */
    private BigDecimal quantity;
    /** 單價，組裝件本身通常沒有直接單價則為 null */
    private BigDecimal unitPrice;
    /** 節點深度，根節點為 0 */
    private Integer level;
    /** 直接父節點的物料編碼，根節點為 null */
    private String parentCode;

    /** 是否有任何方案替代此主料（可能同時被多個方案「部分覆蓋」） */
    private boolean hasSubstitute;
    /** 套用在這顆主料上的替代資訊清單 */
    private List<SubstituteInfoResponse> substituteInfos;

    /** 子節點清單（葉節點為空清單） */
    private List<BomNodeResponse> children;
}
