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

    // 原始主料資訊
    private String itemCode;
    private String itemName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private Integer level;
    private String parentCode;

    // 目前實際生效的料（若有替代料則反映替代料，否則與主料相同）
    private String effectiveItemCode;
    private String effectiveItemName;
    private BigDecimal effectiveUnitPrice;

    private boolean hasSubstitute;
    private SubstituteInfoResponse substituteInfo;

    private List<BomNodeResponse> children;
}
