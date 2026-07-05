package com.delta.bom.service;

import com.delta.bom.dto.request.MaterialRequest;
import com.delta.bom.dto.response.MaterialResponse;

import java.util.List;

public interface MaterialService {

    /**
     * 新增一筆物料主檔。
     *
     * @param request 物料編碼、名稱、單位、單價
     * @return 新建立的物料內容
     */
    MaterialResponse createMaterial(MaterialRequest request);

    /**
     * 列出所有物料主檔。
     *
     * @return 物料清單
     */
    List<MaterialResponse> listMaterials();

    /**
     * 更新一筆物料主檔。
     *
     * @param materialCode 要更新的物料編碼
     * @param request      新的名稱、單位、單價，並帶上目前的 version 以偵測並發覆寫
     * @return 更新後的物料內容
     */
    MaterialResponse updateMaterial(String materialCode, MaterialRequest request);

    /**
     * 刪除一筆物料主檔（若仍被任何 BOM 組成關係或替代方案規則引用，會擋下並列出引用位置）。
     *
     * @param materialCode 要刪除的物料編碼
     */
    void deleteMaterial(String materialCode);
}
