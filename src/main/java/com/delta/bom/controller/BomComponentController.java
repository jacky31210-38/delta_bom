package com.delta.bom.controller;

import com.delta.bom.dto.request.BomComponentCreateRequest;
import com.delta.bom.dto.request.BomComponentUpdateRequest;
import com.delta.bom.dto.response.ApiResponse;
import com.delta.bom.dto.response.BomComponentResponse;
import com.delta.bom.service.BomComponentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bom-components")
@RequiredArgsConstructor
@Tag(name = "BOM 結構維護", description = "物料對物料的組成關係維護（誰的 BOM 裡包含誰、數量多少）")
public class BomComponentController {

    private final BomComponentService bomComponentService;

    @PostMapping
    @Operation(
        summary = "新增 BOM 組成關係",
        description = "定義「父物料的 BOM 裡包含子物料，數量多少」，只需定義一次，" +
                      "任何其他引用父物料的地方都會共用同一份子物料組成，不需要重複建立。\n\n" +
                      "會檢查父／子物料皆存在、不會造成循環依賴（子物料的下游不可以再繞回父物料）。"
    )
    public ApiResponse<BomComponentResponse> createComponent(@RequestBody @Valid BomComponentCreateRequest request) {
        return ApiResponse.success(bomComponentService.createComponent(request));
    }

    @GetMapping
    @Operation(summary = "列出所有 BOM 組成關係", description = "回傳系統中所有「父物料→子物料」的組成關係扁平清單。")
    public ApiResponse<List<BomComponentResponse>> listAll() {
        return ApiResponse.success(bomComponentService.listAll());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新組成數量", description = "只允許調整數量；若要更換父物料或子物料，請刪除後重新建立。")
    public ApiResponse<BomComponentResponse> updateComponentQuantity(
        @Parameter(description = "組成關係 id") @PathVariable Long id,
        @RequestBody @Valid BomComponentUpdateRequest request
    ) {
        return ApiResponse.success(bomComponentService.updateComponentQuantity(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "刪除組成關係",
        description = "只移除這一條「父物料包含子物料」的關係，子物料自己的組成不受影響，仍可被其他地方引用。"
    )
    public ApiResponse<Void> deleteComponent(
        @Parameter(description = "組成關係 id") @PathVariable Long id
    ) {
        bomComponentService.deleteComponent(id);
        return ApiResponse.success(null);
    }
}
