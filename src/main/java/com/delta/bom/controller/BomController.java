package com.delta.bom.controller;

import com.delta.bom.dto.request.BomQueryRequest;
import com.delta.bom.dto.request.SubstitutionInput;
import com.delta.bom.dto.response.*;
import com.delta.bom.service.BomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/bom")
@RequiredArgsConstructor
@Tag(name = "BOM 管理", description = "BOM 結構查詢、成本計算")
public class BomController {

    private final BomService bomService;

    @PostMapping("/{rootCode}/structure")
    @Operation(
        summary = "查詢 BOM 完整結構",
        description = "回傳指定根節點的完整樹狀結構。\n\n" +
                      "substitutions 留空：回傳原始 BOM，不套用任何替代。\n" +
                      "substitutions 每筆指定「套用哪個方案的哪顆主料、這次替換多少數量」，" +
                      "允許同一顆主料被多個方案的規則共同覆蓋（例如各補一半數量），" +
                      "但合計數量不可超過該主料的 BOM 需求量，否則回傳 400。\n\n" +
                      "查詢條件較複雜（結構化清單），故用 POST 帶 body，語意上仍是唯讀查詢。"
    )
    public ApiResponse<BomNodeResponse> getBomStructure(
        @Parameter(description = "根節點物料編碼，例如 PCB-CONTROL-V2")
        @PathVariable String rootCode,
        @RequestBody(required = false) @Valid BomQueryRequest request
    ) {
        List<SubstitutionInput> substitutions = normalize(request);
        return ApiResponse.success(bomService.getBomStructure(rootCode, substitutions));
    }

    @PostMapping("/{rootCode}/cost")
    @Operation(
        summary = "計算 BOM 總成本",
        description = "計算所有葉節點成本並加總，考量各層數量累乘與替代比例。\n\n" +
                      "substitutions 留空：以原始 BOM 計算成本。\n" +
                      "substitutions 每筆指定「套用哪個方案的哪顆主料、這次替換多少數量」，" +
                      "details 列出每一行明細（一顆主料可能因跨方案套用而拆成多行）。"
    )
    public ApiResponse<BomCostResponse> calculateCost(
        @Parameter(description = "根節點物料編碼，例如 PCB-CONTROL-V2")
        @PathVariable String rootCode,
        @RequestBody(required = false) @Valid BomQueryRequest request
    ) {
        List<SubstitutionInput> substitutions = normalize(request);
        return ApiResponse.success(bomService.calculateCost(rootCode, substitutions));
    }

    /**
     * 依 (scenarioKey, primaryCode) 排序，讓 @Cacheable 的 key（依 List.toString()）
     * 在輸入順序不同但內容相同時，仍能命中同一筆快取。
     */
    private List<SubstitutionInput> normalize(BomQueryRequest request) {
        if (request == null || request.getSubstitutions() == null || request.getSubstitutions().isEmpty()) {
            return null;
        }
        return request.getSubstitutions().stream()
            .sorted(Comparator.comparing(SubstitutionInput::getScenarioKey)
                .thenComparing(SubstitutionInput::getPrimaryCode))
            .toList();
    }
}
