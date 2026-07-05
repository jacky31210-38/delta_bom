package com.delta.bom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScenarioItemRequest {

    /** 所屬方案代碼；不驗證，由 controller 從路徑變數 scenarioKey 填入，不會出現在 request body 中 */
    private String scenarioKey;

    /** 主料編碼（要被替換的物料） */
    @NotBlank(message = "主料編碼不可為空")
    private String primaryMaterialCode;

    /** 替代料編碼 */
    @NotBlank(message = "替代料編碼不可為空")
    private String substituteMaterialCode;

    /** 替代原因，選填 */
    private String reason;

    /** 替代比例：1 顆主料對應幾顆替代料，未填預設 1（1:1 替換） */
    @DecimalMin(value = "0.0001", message = "替代比例必須大於 0")
    private BigDecimal substituteRatio;

    /** 更新既有規則時必填：前端讀取資料當下拿到的版本號，用來偵測並發覆寫；新增時不需要，可留空 */
    private Integer version;
}
