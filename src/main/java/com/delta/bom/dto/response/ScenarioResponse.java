package com.delta.bom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioResponse {

    private Long id;
    private String scenarioKey;
    private String scenarioName;
    private String description;
    private long itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
