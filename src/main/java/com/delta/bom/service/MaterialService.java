package com.delta.bom.service;

import com.delta.bom.dto.request.MaterialRequest;
import com.delta.bom.dto.response.MaterialResponse;

import java.util.List;

public interface MaterialService {

    MaterialResponse createMaterial(MaterialRequest request);

    List<MaterialResponse> listMaterials();

    MaterialResponse updateMaterial(String materialCode, MaterialRequest request);

    void deleteMaterial(String materialCode);
}
