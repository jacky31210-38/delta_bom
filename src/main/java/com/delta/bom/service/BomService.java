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

    BomNodeResponse getBomStructure(String rootCode, List<SubstitutionInput> substitutions);

    BomCostResponse calculateCost(String rootCode, List<SubstitutionInput> substitutions);

    ScenarioResponse createScenario(ScenarioRequest request);

    List<ScenarioResponse> listScenarios();

    void deleteScenario(String scenarioKey);

    ScenarioItemResponse upsertScenarioItem(ScenarioItemRequest request);

    List<ScenarioItemResponse> listScenarioItems(String scenarioKey);

    List<ScenarioItemResponse> listAllScenarioItems();

    void deleteScenarioItem(String scenarioKey, String primaryCode);
}
