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

    /** 這筆替代來自哪個方案 */
    private String scenarioKey;
    /** 替代料編碼 */
    private String substituteCode;
    /** 替代料名稱 */
    private String substituteName;
    /** 覆蓋主料 BOM 需求量中的多少數量 */
    private BigDecimal substituteQty;
    /** 替代比例：1 顆主料對應幾顆替代料 */
    private BigDecimal substituteRatio;
    /** 替代料單價 */
    private BigDecimal unitPrice;
    /** 替代原因 */
    private String reason;
}
