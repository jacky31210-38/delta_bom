package com.delta.bom.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScenarioRequest {

    /** 方案代碼，需全域唯一，作為方案的識別鍵 */
    @NotBlank(message = "方案 key 不可為空")
    private String scenarioKey;

    /** 方案顯示名稱 */
    @NotBlank(message = "方案名稱不可為空")
    private String scenarioName;

    /** 方案描述，選填 */
    private String description;
}
