package com.delta.bom.controller;

import com.delta.bom.dto.request.ScenarioItemRequest;
import com.delta.bom.dto.request.ScenarioRequest;
import com.delta.bom.dto.response.ApiResponse;
import com.delta.bom.dto.response.ScenarioItemResponse;
import com.delta.bom.dto.response.ScenarioResponse;
import com.delta.bom.service.BomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
@Tag(name = "替代方案管理", description = "替代方案（scenario）與方案內替代料配對的維護")
public class ScenarioController {

    private final BomService bomService;

    /**
     * 建立一個新的替代方案分組。
     *
     * @param request 方案 key（需全域唯一）、名稱、描述
     * @return 新建立的方案內容
     */
    @PostMapping
    @Operation(summary = "新增替代方案", description = "建立一個新的替代方案分組，scenarioKey 需全域唯一。")
    public ApiResponse<ScenarioResponse> createScenario(@RequestBody @Valid ScenarioRequest request) {
        return ApiResponse.success(bomService.createScenario(request));
    }

    /**
     * 列出所有已建立的替代方案。
     *
     * @return 方案清單，含每個方案內的配對數量
     */
    @GetMapping
    @Operation(summary = "列出所有替代方案", description = "回傳所有已建立的替代方案，含每個方案內的配對數量。")
    public ApiResponse<List<ScenarioResponse>> listScenarios() {
        return ApiResponse.success(bomService.listScenarios());
    }

    /**
     * 刪除方案本身及其底下所有替代料配對。
     *
     * @param scenarioKey 方案 key，例如 MULTI_SOURCE
     * @return 空回應
     */
    @DeleteMapping("/{scenarioKey}")
    @Operation(summary = "刪除替代方案", description = "刪除方案本身及其底下所有替代料配對。")
    public ApiResponse<Void> deleteScenario(
        @Parameter(description = "方案 key，例如 MULTI_SOURCE")
        @PathVariable String scenarioKey
    ) {
        bomService.deleteScenario(scenarioKey);
        return ApiResponse.success(null);
    }

    /**
     * 不限方案，查詢資料庫中目前所有的「主料 → 替代料」配對規則。
     *
     * @return 所有方案的替代料配對清單
     */
    @GetMapping("/items")
    @Operation(
        summary = "查詢所有方案的替代料明細",
        description = "不限方案，回傳資料庫中目前所有的「主料 → 替代料」配對規則，方便一次瀏覽/搜尋整體替代料設定" +
                      "（例如想知道某顆主料目前被哪些方案定義了替代規則）。前端可自行依關鍵字篩選回傳結果。"
    )
    public ApiResponse<List<ScenarioItemResponse>> listAllScenarioItems() {
        return ApiResponse.success(bomService.listAllScenarioItems());
    }

    /**
     * 在指定方案內建立或更新一筆「主料 → 替代料」規則（有則更新、無則新增）。
     *
     * @param scenarioKey 方案 key，例如 MULTI_SOURCE
     * @param request     主料編碼、替代料編碼、比例、原因；更新既有規則時需一併帶上目前的 version
     * @return 新增或更新後的規則內容
     */
    @PostMapping("/{scenarioKey}/items")
    @Operation(
        summary = "套用方案內的替代料配對（UPSERT）",
        description = "在指定方案內建立或更新一筆「主料 → 替代料」規則（比例、單價），不含數量——" +
                      "數量是查詢 BOM 結構／計算成本時才動態輸入。\n\n" +
                      "- 有則更新（更換替代料、調整比例/單價）\n" +
                      "- 無則新增"
    )
    public ApiResponse<ScenarioItemResponse> upsertScenarioItem(
        @Parameter(description = "方案 key，例如 MULTI_SOURCE")
        @PathVariable String scenarioKey,
        @RequestBody @Valid ScenarioItemRequest request
    ) {
        request.setScenarioKey(scenarioKey);
        return ApiResponse.success(bomService.upsertScenarioItem(request));
    }

    /**
     * 查詢指定方案底下目前所有的「主料 → 替代料」配對。
     *
     * @param scenarioKey 方案 key，例如 MULTI_SOURCE
     * @return 該方案內的替代料配對清單
     */
    @GetMapping("/{scenarioKey}/items")
    @Operation(summary = "列出方案內所有替代料配對", description = "查詢指定方案底下目前所有的「主料 → 替代料」配對。")
    public ApiResponse<List<ScenarioItemResponse>> listScenarioItems(
        @Parameter(description = "方案 key，例如 MULTI_SOURCE")
        @PathVariable String scenarioKey
    ) {
        return ApiResponse.success(bomService.listScenarioItems(scenarioKey));
    }

    /**
     * 移除指定方案內某顆主料的替代料配對。
     *
     * @param scenarioKey 方案 key，例如 MULTI_SOURCE
     * @param primaryCode 主料編碼，例如 IC-MCU
     * @return 空回應
     */
    @DeleteMapping("/{scenarioKey}/items/{primaryCode}")
    @Operation(summary = "刪除方案內的一組替代料配對", description = "移除指定方案內某顆主料的替代料配對。")
    public ApiResponse<Void> deleteScenarioItem(
        @Parameter(description = "方案 key，例如 MULTI_SOURCE")
        @PathVariable String scenarioKey,
        @Parameter(description = "主料編碼，例如 IC-MCU")
        @PathVariable String primaryCode
    ) {
        bomService.deleteScenarioItem(scenarioKey, primaryCode);
        return ApiResponse.success(null);
    }
}
