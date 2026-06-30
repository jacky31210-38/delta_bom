package com.delta.bom.service;

import com.delta.bom.dto.request.SubstituteRequest;
import com.delta.bom.dto.response.BomCostResponse;
import com.delta.bom.dto.response.BomNodeResponse;
import com.delta.bom.dto.response.SubstituteResponse;

public interface BomService {

    BomNodeResponse getBomStructure(String rootCode);

    BomCostResponse calculateCost(String rootCode);

    void applySubstitute(SubstituteRequest request);

    SubstituteResponse getSubstitute(String primaryCode);
}
