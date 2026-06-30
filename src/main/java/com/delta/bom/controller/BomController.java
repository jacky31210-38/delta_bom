package com.delta.bom.controller;

import com.delta.bom.dto.request.SubstituteRequest;
import com.delta.bom.dto.response.*;
import com.delta.bom.service.BomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bom")
@RequiredArgsConstructor
@Tag(name = "BOM 管理", description = "BOM 結構查詢、成本計算、替代料管理")
public class BomController {

    private final BomService bomService;

    @GetMapping("/{rootCode}")
    @Operation(
        summary = "查詢 BOM 完整結構",
        description = "回傳指定根節點的完整樹狀結構。若節點已套用替代料，" +
                      "effectiveItemCode 會反映替代料，substituteInfo 顯示替代詳情。"
    )
    public ApiResponse<BomNodeResponse> getBomStructure(
        @Parameter(description = "根節點物料編碼，例如 PCB-CONTROL-V2")
        @PathVariable String rootCode
    ) {
        return ApiResponse.success(bomService.getBomStructure(rootCode));
    }

    @GetMapping("/{rootCode}/cost")
    @Operation(
        summary = "計算 BOM 總成本",
        description = "計算所有葉節點成本並加總，考量各層數量累乘與替代料（含部分替換）。" +
                      "details 列出每顆葉料的有效數量、單價與小計。"
    )
    public ApiResponse<BomCostResponse> calculateCost(
        @Parameter(description = "根節點物料編碼，例如 PCB-CONTROL-V2")
        @PathVariable String rootCode
    ) {
        return ApiResponse.success(bomService.calculateCost(rootCode));
    }

    @PostMapping("/substitute")
    @Operation(
        summary = "套用替代料（UPSERT）",
        description = "建立或更新主料的替代料關係，並自動清除 BOM 結構與成本快取。\n\n" +
                      "- 有則更新（更換替代料、調整數量/單價）\n" +
                      "- 無則新增\n" +
                      "- substituteQty 不可超過主料在 BOM 中的數量"
    )
    public ApiResponse<Void> applySubstitute(@RequestBody @Valid SubstituteRequest request) {
        bomService.applySubstitute(request);
        return ApiResponse.success(null);
    }

    @GetMapping("/substitute/{primaryCode}")
    @Operation(
        summary = "查詢替代料",
        description = "查詢指定主料目前有效的替代料資訊。若尚未套用替代料則回傳 404。"
    )
    public ApiResponse<SubstituteResponse> getSubstitute(
        @Parameter(description = "主料物料編碼，例如 IC-MCU")
        @PathVariable String primaryCode
    ) {
        return ApiResponse.success(bomService.getSubstitute(primaryCode));
    }
}
