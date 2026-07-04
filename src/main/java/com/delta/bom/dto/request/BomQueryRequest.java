package com.delta.bom.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class BomQueryRequest {

    /**
     * 這次查詢/計算成本要套用的替代配對清單。留空或不帶代表原始 BOM，不套用任何替代。
     */
    @Valid
    private List<SubstitutionInput> substitutions;
}
