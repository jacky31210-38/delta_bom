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
public class MaterialResponse {

    private Long id;
    private String materialCode;
    private String materialName;
    private String unit;
    private BigDecimal unitPrice;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
