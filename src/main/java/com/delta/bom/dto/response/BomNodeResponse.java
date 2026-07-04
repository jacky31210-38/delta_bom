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

    // 是否有任何方案替代此主料（可能同時被多個方案「部分覆蓋」）
    private boolean hasSubstitute;
    private List<SubstituteInfoResponse> substituteInfos;

    private List<BomNodeResponse> children;
}
