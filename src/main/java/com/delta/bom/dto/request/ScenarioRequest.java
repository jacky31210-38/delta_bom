package com.delta.bom.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScenarioRequest {

    @NotBlank(message = "方案 key 不可為空")
    private String scenarioKey;

    @NotBlank(message = "方案名稱不可為空")
    private String scenarioName;

    private String description;
}
