package com.delta.bom.service;

import com.delta.bom.dto.request.BomComponentCreateRequest;
import com.delta.bom.dto.request.BomComponentUpdateRequest;
import com.delta.bom.dto.response.BomComponentResponse;
import com.delta.bom.dto.response.RootMaterialResponse;

import java.util.List;

public interface BomComponentService {

    /**
     * 新增一條「父物料包含子物料」的 BOM 組成關係。
     *
     * @param request 父物料編碼、子物料編碼、數量
     * @return 新建立的組成關係內容
     */
    BomComponentResponse createComponent(BomComponentCreateRequest request);

    /**
     * 列出系統中所有的 BOM 組成關係。
     *
     * @return 所有「父物料→子物料」組成關係的扁平清單
     */
    List<BomComponentResponse> listAll();

    /**
     * 更新一條組成關係的數量。
     *
     * @param id      組成關係 id
     * @param request 新的數量，並帶上目前的 version 以偵測並發覆寫
     * @return 更新後的組成關係內容
     */
    BomComponentResponse updateComponentQuantity(Long id, BomComponentUpdateRequest request);

    /**
     * 刪除一條組成關係（只移除這條父子關係，子物料自己的組成不受影響，仍可被其他地方引用）。
     *
     * @param id 組成關係 id
     */
    void deleteComponent(Long id);

    /**
     * 列出所有「從未被當作子物料使用過」的物料，視為各個 BOM 樹的根節點。
     *
     * @return 根節點物料清單
     */
    List<RootMaterialResponse> listRoots();
}
