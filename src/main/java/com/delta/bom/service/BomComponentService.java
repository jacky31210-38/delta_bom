package com.delta.bom.service;

import com.delta.bom.dto.request.BomComponentCreateRequest;
import com.delta.bom.dto.request.BomComponentUpdateRequest;
import com.delta.bom.dto.response.BomComponentResponse;
import com.delta.bom.dto.response.RootMaterialResponse;

import java.util.List;

public interface BomComponentService {

    BomComponentResponse createComponent(BomComponentCreateRequest request);

    List<BomComponentResponse> listAll();

    BomComponentResponse updateComponentQuantity(Long id, BomComponentUpdateRequest request);

    void deleteComponent(Long id);

    List<RootMaterialResponse> listRoots();
}
