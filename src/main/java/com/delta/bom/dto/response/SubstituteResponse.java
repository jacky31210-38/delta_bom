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
public class SubstituteResponse {

    private Long id;
    private String primaryCode;
    private String substituteCode;
    private String substituteName;
    private String reason;
    private BigDecimal substituteQty;
    private BigDecimal unitPrice;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
