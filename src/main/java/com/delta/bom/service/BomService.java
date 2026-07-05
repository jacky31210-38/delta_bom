package com.delta.bom.service;

import com.delta.bom.dto.request.ScenarioItemRequest;
import com.delta.bom.dto.request.ScenarioRequest;
import com.delta.bom.dto.request.SubstitutionInput;
import com.delta.bom.dto.response.BomCostResponse;
import com.delta.bom.dto.response.BomNodeResponse;
import com.delta.bom.dto.response.ScenarioItemResponse;
import com.delta.bom.dto.response.ScenarioResponse;

import java.util.List;

public interface BomService {

    /**
     * 查詢指定根節點物料的完整 BOM 樹狀結構。
     *
     * @param rootCode      根節點物料編碼
     * @param substitutions 這次查詢要套用的替代規則清單（方案 key、主料編碼、替換數量），null 或空清單代表不套用任何替代
     * @return 展開後的樹狀結構，含每個節點是否有套用替代、替代明細
     */
    BomNodeResponse getBomStructure(String rootCode, List<SubstitutionInput> substitutions);

    /**
     * 計算指定根節點物料的 BOM 總成本。
     *
     * @param rootCode      根節點物料編碼
     * @param substitutions 這次計算要套用的替代規則清單，null 或空清單代表以原始 BOM 計算
     * @return 總成本與每一筆計價明細
     */
    BomCostResponse calculateCost(String rootCode, List<SubstitutionInput> substitutions);

    /**
     * 建立一個新的替代方案分組。
     *
     * @param request 方案 key、名稱、描述
     * @return 新建立的方案內容
     */
    ScenarioResponse createScenario(ScenarioRequest request);

    /**
     * 列出所有已建立的替代方案。
     *
     * @return 方案清單，含每個方案底下的規則筆數
     */
    List<ScenarioResponse> listScenarios();

    /**
     * 刪除一個替代方案，含其底下所有替代規則。
     *
     * @param scenarioKey 要刪除的方案 key
     */
    void deleteScenario(String scenarioKey);

    /**
     * 在指定方案內新增或更新一筆「主料 → 替代料」規則。
     *
     * @param request 方案 key、主料編碼、替代料編碼、比例、原因；更新既有規則時需一併帶上目前的 version 以偵測並發覆寫
     * @return 新增或更新後的規則內容
     */
    ScenarioItemResponse upsertScenarioItem(ScenarioItemRequest request);

    /**
     * 列出指定方案內的所有替代規則。
     *
     * @param scenarioKey 方案 key
     * @return 該方案底下的規則清單
     */
    List<ScenarioItemResponse> listScenarioItems(String scenarioKey);

    /**
     * 列出資料庫中所有方案的所有替代規則，不限定方案。
     *
     * @return 所有方案的規則清單
     */
    List<ScenarioItemResponse> listAllScenarioItems();

    /**
     * 刪除指定方案內、指定主料的替代規則。
     *
     * @param scenarioKey 方案 key
     * @param primaryCode 要移除規則的主料編碼
     */
    void deleteScenarioItem(String scenarioKey, String primaryCode);
}
