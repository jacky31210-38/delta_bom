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

    private String substituteCode;
    private String substituteName;
    private BigDecimal substituteQty;
    private BigDecimal unitPrice;
    private String reason;
}
