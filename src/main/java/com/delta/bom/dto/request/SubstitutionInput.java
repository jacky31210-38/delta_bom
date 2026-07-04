package com.delta.bom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 查詢/計算成本時，指定「套用哪個方案的哪筆規則，這次要替換多少數量」。
 * (scenarioKey, primaryCode) 唯一對應一筆 substitute_scenario_item，
 * 比例、單價、替代料編碼等都從該筆規則查得，這裡只帶動態的數量。
 */
@Data
public class SubstitutionInput {

    @NotBlank(message = "方案 key 不可為空")
    private String scenarioKey;

    @NotBlank(message = "主料編碼不可為空")
    private String primaryCode;

    @NotNull(message = "替代數量不可為空")
    @DecimalMin(value = "0.0001", message = "替代數量必須大於 0")
    private BigDecimal qty;

    /**
     * 正規化後的字串表示，用於 @Cacheable 的 key 組成（見 BomController#normalize）。
     */
    @Override
    public String toString() {
        return scenarioKey + "/" + primaryCode + "/" + qty;
    }
}
