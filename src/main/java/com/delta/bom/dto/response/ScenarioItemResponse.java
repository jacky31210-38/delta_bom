package com.delta.bom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioItemResponse {

    private Long id;
    private String scenarioKey;
    private String primaryCode;
    private String substituteCode;
    private String substituteName;
    private String reason;
    private BigDecimal substituteRatio;
    private BigDecimal unitPrice;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
