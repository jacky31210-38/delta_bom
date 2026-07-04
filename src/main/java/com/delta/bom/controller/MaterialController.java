package com.delta.bom.controller;

import com.delta.bom.dto.request.MaterialRequest;
import com.delta.bom.dto.response.ApiResponse;
import com.delta.bom.dto.response.MaterialResponse;
import com.delta.bom.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
@Tag(name = "物料主檔管理", description = "物料（名稱、單位、單價）的維護，供 BOM 結構與替代方案共用")
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping
    @Operation(summary = "新增物料", description = "建立一筆新的物料主檔，materialCode 需全域唯一。")
    public ApiResponse<MaterialResponse> createMaterial(@RequestBody @Valid MaterialRequest request) {
        return ApiResponse.success(materialService.createMaterial(request));
    }

    @GetMapping
    @Operation(summary = "列出所有物料", description = "回傳目前所有物料主檔，供管理頁面與各處的物料搜尋下拉使用。")
    public ApiResponse<List<MaterialResponse>> listMaterials() {
        return ApiResponse.success(materialService.listMaterials());
    }

    @PutMapping("/{materialCode}")
    @Operation(summary = "更新物料", description = "更新物料名稱、單位、單價。")
    public ApiResponse<MaterialResponse> updateMaterial(
        @Parameter(description = "物料編碼，例如 IC-MCU")
        @PathVariable String materialCode,
        @RequestBody @Valid MaterialRequest request
    ) {
        return ApiResponse.success(materialService.updateMaterial(materialCode, request));
    }

    @DeleteMapping("/{materialCode}")
    @Operation(
        summary = "刪除物料",
        description = "若該物料仍被任何 BOM 結構節點或替代方案規則引用，會回傳 400 並列出引用位置，需先移除引用才能刪除。"
    )
    public ApiResponse<Void> deleteMaterial(
        @Parameter(description = "物料編碼，例如 IC-MCU")
        @PathVariable String materialCode
    ) {
        materialService.deleteMaterial(materialCode);
        return ApiResponse.success(null);
    }
}
