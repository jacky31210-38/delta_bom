package com.delta.bom.service.impl;

import com.delta.bom.dto.request.ScenarioItemRequest;
import com.delta.bom.dto.request.SubstitutionInput;
import com.delta.bom.dto.response.BomCostResponse;
import com.delta.bom.dto.response.ScenarioItemResponse;
import com.delta.bom.entity.BomComponent;
import com.delta.bom.entity.Material;
import com.delta.bom.entity.SubstituteScenario;
import com.delta.bom.entity.SubstituteScenarioItem;
import com.delta.bom.exception.BomNotFoundException;
import com.delta.bom.exception.BusinessException;
import com.delta.bom.exception.OptimisticLockConflictException;
import com.delta.bom.exception.ScenarioNotFoundException;
import com.delta.bom.mapper.BomComponentMapper;
import com.delta.bom.mapper.MaterialMapper;
import com.delta.bom.mapper.SubstituteScenarioItemMapper;
import com.delta.bom.mapper.SubstituteScenarioMapper;
import com.delta.bom.service.MaterialFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 涵蓋 BomServiceImpl 最容易出錯的部分：跨層數量累乘的成本計算、
 * 替代方案的比例/部分覆蓋換算、超量與循環依賴的防禦性驗證。
 * 這裡測的是 buildBomTree/buildNode/calcCostDfs 這幾個私有邏輯透過公開方法間接驗證的行為，
 * 資料存取層（mapper）全部用 Mockito mock，不牽涉真實資料庫。
 */
@ExtendWith(MockitoExtension.class)
class BomServiceImplTest {

    @Mock
    private BomComponentMapper bomComponentMapper;
    @Mock
    private MaterialMapper materialMapper;
    @Mock
    private SubstituteScenarioMapper scenarioMapper;
    @Mock
    private SubstituteScenarioItemMapper scenarioItemMapper;
    @Mock
    private MaterialFinder materialFinder;

    @InjectMocks
    private BomServiceImpl bomService;

    /**
     * 兩層樹：ROOT 包含 CHILD_A × 2，CHILD_A 包含 LEAF × 3（單價 10）。
     * 沒有替代方案時，LEAF 的有效數量 = 2 × 3 = 6，成本 = 6 × 10 = 60。
     * 只有實際會展開這棵樹的測試才呼叫，避免其他測試背著用不到的 stub
     * （Mockito 嚴格模式會把沒被用到的 stub 視為錯誤，逼你的 arrange 跟實際行為對齊）。
     */
    private void stubRootTree(Material... extraMaterials) {
        Material root = Material.builder().materialCode("ROOT").materialName("Root").build();
        Material childA = Material.builder().materialCode("CHILD_A").materialName("Child A").build();
        Material leaf = Material.builder().materialCode("LEAF").materialName("Leaf").unitPrice(new BigDecimal("10.00")).build();

        BomComponent rootToChildA = BomComponent.builder()
            .parentMaterialCode("ROOT").childMaterialCode("CHILD_A").quantity(new BigDecimal("2")).build();
        BomComponent childAToLeaf = BomComponent.builder()
            .parentMaterialCode("CHILD_A").childMaterialCode("LEAF").quantity(new BigDecimal("3")).build();

        when(bomComponentMapper.selectSubtreeEdges("ROOT")).thenReturn(List.of(rootToChildA, childAToLeaf));

        List<Material> materials = new ArrayList<>(List.of(root, childA, leaf));
        materials.addAll(List.of(extraMaterials));
        when(materialMapper.selectList(any())).thenReturn(materials);
    }

    @Test
    void calculateCost_withoutSubstitution_rollsUpQuantityAcrossLevels() {
        stubRootTree();

        BomCostResponse response = bomService.calculateCost("ROOT", null);

        assertThat(response.getTotalCost()).isEqualByComparingTo("60.0000");
    }

    @Test
    void calculateCost_withPartialSubstitution_splitsCostBetweenSubstituteAndOriginal() {
        Material altMaterial = Material.builder().materialCode("ALT").materialName("替代葉件").unitPrice(new BigDecimal("7.00")).build();
        stubRootTree(altMaterial);
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        SubstituteScenarioItem rule = SubstituteScenarioItem.builder()
            .scenarioKey("S1").primaryCode("LEAF").substituteCode("ALT")
            .substituteRatio(new BigDecimal("2")).build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(scenarioItemMapper.selectOne(any())).thenReturn(rule);

        SubstitutionInput input = new SubstitutionInput();
        input.setScenarioKey("S1");
        input.setPrimaryCode("LEAF");
        input.setQty(new BigDecimal("2")); // LEAF 本身數量是 3，只覆蓋 2，剩 1 用原價

        BomCostResponse response = bomService.calculateCost("ROOT", List.of(input));

        // 替代：2(覆蓋量) × 2(父層倍乘) × 2(比例) × 7 = 56；剩餘：1 × 2 × 10 = 20；合計 76
        assertThat(response.getTotalCost()).isEqualByComparingTo("76.0000");
        assertThat(response.getDetails()).hasSize(2);
        assertThat(response.getDetails()).anySatisfy(d -> assertThat(d.isSubstituted()).isTrue());
        assertThat(response.getDetails()).anySatisfy(d -> assertThat(d.isSubstituted()).isFalse());
    }

    @Test
    void calculateCost_substitutionExceedsBomQuantity_throwsBusinessException() {
        stubRootTree();
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        SubstituteScenarioItem rule = SubstituteScenarioItem.builder()
            .scenarioKey("S1").primaryCode("LEAF").substituteCode("ALT")
            .substituteRatio(BigDecimal.ONE).build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(scenarioItemMapper.selectOne(any())).thenReturn(rule);

        SubstitutionInput input = new SubstitutionInput();
        input.setScenarioKey("S1");
        input.setPrimaryCode("LEAF");
        input.setQty(new BigDecimal("5")); // LEAF 本身只需要 3，5 超過了

        assertThatThrownBy(() -> bomService.calculateCost("ROOT", List.of(input)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("超過主料 BOM 數量");
    }

    @Test
    void calculateCost_scenarioKeyNotFound_throwsScenarioNotFoundException() {
        // 方案解析發生在展開 BOM 結構之前，不需要 stub 任何 BOM/物料資料
        when(scenarioMapper.selectOne(any())).thenReturn(null);

        SubstitutionInput input = new SubstitutionInput();
        input.setScenarioKey("NOT-EXIST");
        input.setPrimaryCode("LEAF");
        input.setQty(BigDecimal.ONE);

        assertThatThrownBy(() -> bomService.calculateCost("ROOT", List.of(input)))
            .isInstanceOf(ScenarioNotFoundException.class);
    }

    @Test
    void getBomStructure_rootMaterialNotFound_throwsBomNotFoundException() {
        when(materialFinder.getOrThrow("MISSING")).thenThrow(new BomNotFoundException("MISSING"));

        assertThatThrownBy(() -> bomService.getBomStructure("MISSING", null))
            .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void getBomStructure_cyclicComponentData_throwsBusinessException() {
        // 正常情況下寫入時就會擋下循環依賴，這裡模擬萬一資料本身已經壞掉（例如直接改 DB）的防禦情境
        BomComponent aToB = BomComponent.builder().parentMaterialCode("A").childMaterialCode("B").quantity(BigDecimal.ONE).build();
        BomComponent bToA = BomComponent.builder().parentMaterialCode("B").childMaterialCode("A").quantity(BigDecimal.ONE).build();
        when(bomComponentMapper.selectSubtreeEdges("A")).thenReturn(List.of(aToB, bToA));
        when(materialMapper.selectList(any())).thenReturn(List.of(
            Material.builder().materialCode("A").materialName("A").build(),
            Material.builder().materialCode("B").materialName("B").build()
        ));

        assertThatThrownBy(() -> bomService.getBomStructure("A", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("循環依賴");
    }

    @Test
    void upsertScenarioItem_createsNewRule_whenNoneExists() {
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(materialFinder.getOrThrow("LEAF")).thenReturn(Material.builder().materialCode("LEAF").materialName("Leaf").build());
        when(materialFinder.getOrThrow("ALT")).thenReturn(
            Material.builder().materialCode("ALT").materialName("替代葉件").unitPrice(new BigDecimal("5")).build());
        when(scenarioItemMapper.selectOne(any())).thenReturn(null);

        ScenarioItemRequest request = new ScenarioItemRequest();
        request.setScenarioKey("S1");
        request.setPrimaryMaterialCode("LEAF");
        request.setSubstituteMaterialCode("ALT");

        ScenarioItemResponse response = bomService.upsertScenarioItem(request);

        assertThat(response.getSubstituteRatio()).isEqualByComparingTo(BigDecimal.ONE); // 未填比例，預設 1
        assertThat(response.getUnitPrice()).isEqualByComparingTo("5"); // 單價即時從物料主檔取得，不是自己存的
    }

    @Test
    void upsertScenarioItem_substituteMaterialHasNoPrice_throwsBusinessException() {
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(materialFinder.getOrThrow("LEAF")).thenReturn(Material.builder().materialCode("LEAF").materialName("Leaf").build());
        when(materialFinder.getOrThrow("ALT")).thenReturn(
            Material.builder().materialCode("ALT").materialName("替代葉件").unitPrice(null).build());

        ScenarioItemRequest request = new ScenarioItemRequest();
        request.setScenarioKey("S1");
        request.setPrimaryMaterialCode("LEAF");
        request.setSubstituteMaterialCode("ALT");

        assertThatThrownBy(() -> bomService.upsertScenarioItem(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("尚未在物料主檔設定單價");
    }

    @Test
    void upsertScenarioItem_updatesExistingRule_whenVersionMatches() {
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(materialFinder.getOrThrow("LEAF")).thenReturn(Material.builder().materialCode("LEAF").materialName("Leaf").build());
        when(materialFinder.getOrThrow("ALT")).thenReturn(
            Material.builder().materialCode("ALT").materialName("替代葉件").unitPrice(new BigDecimal("5")).build());
        SubstituteScenarioItem existing = SubstituteScenarioItem.builder()
            .id(1L).scenarioKey("S1").primaryCode("LEAF").substituteCode("OLD-ALT")
            .substituteRatio(BigDecimal.ONE).version(0).build();
        when(scenarioItemMapper.selectOne(any())).thenReturn(existing);
        when(scenarioItemMapper.updateById(existing)).thenReturn(1);

        ScenarioItemRequest request = new ScenarioItemRequest();
        request.setScenarioKey("S1");
        request.setPrimaryMaterialCode("LEAF");
        request.setSubstituteMaterialCode("ALT");
        request.setVersion(0);

        ScenarioItemResponse response = bomService.upsertScenarioItem(request);

        assertThat(response.getSubstituteCode()).isEqualTo("ALT");
    }

    @Test
    void upsertScenarioItem_updateWithoutVersion_throwsBusinessException() {
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(materialFinder.getOrThrow("LEAF")).thenReturn(Material.builder().materialCode("LEAF").materialName("Leaf").build());
        when(materialFinder.getOrThrow("ALT")).thenReturn(
            Material.builder().materialCode("ALT").materialName("替代葉件").unitPrice(new BigDecimal("5")).build());
        SubstituteScenarioItem existing = SubstituteScenarioItem.builder()
            .id(1L).scenarioKey("S1").primaryCode("LEAF").substituteCode("OLD-ALT")
            .substituteRatio(BigDecimal.ONE).version(0).build();
        when(scenarioItemMapper.selectOne(any())).thenReturn(existing);

        ScenarioItemRequest request = new ScenarioItemRequest();
        request.setScenarioKey("S1");
        request.setPrimaryMaterialCode("LEAF");
        request.setSubstituteMaterialCode("ALT");

        assertThatThrownBy(() -> bomService.upsertScenarioItem(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必須提供 version");
    }

    @Test
    void upsertScenarioItem_updateVersionConflict_throwsOptimisticLockConflictException() {
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(materialFinder.getOrThrow("LEAF")).thenReturn(Material.builder().materialCode("LEAF").materialName("Leaf").build());
        when(materialFinder.getOrThrow("ALT")).thenReturn(
            Material.builder().materialCode("ALT").materialName("替代葉件").unitPrice(new BigDecimal("5")).build());
        SubstituteScenarioItem existing = SubstituteScenarioItem.builder()
            .id(1L).scenarioKey("S1").primaryCode("LEAF").substituteCode("OLD-ALT")
            .substituteRatio(BigDecimal.ONE).version(0).build();
        when(scenarioItemMapper.selectOne(any())).thenReturn(existing);
        // 模擬別人已經搶先更新過，version 已經不是 0 了：WHERE version = 0 比對不到任何一列，回傳受影響筆數 0
        when(scenarioItemMapper.updateById(existing)).thenReturn(0);

        ScenarioItemRequest request = new ScenarioItemRequest();
        request.setScenarioKey("S1");
        request.setPrimaryMaterialCode("LEAF");
        request.setSubstituteMaterialCode("ALT");
        request.setVersion(0);

        assertThatThrownBy(() -> bomService.upsertScenarioItem(request))
            .isInstanceOf(OptimisticLockConflictException.class)
            .hasMessageContaining("已被其他人修改");
    }

    @Test
    void upsertScenarioItem_primaryMaterialNotFound_throwsBomNotFoundException() {
        SubstituteScenario scenario = SubstituteScenario.builder().scenarioKey("S1").scenarioName("測試方案").build();
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
        when(materialFinder.getOrThrow("MISSING")).thenThrow(new BomNotFoundException("MISSING"));

        ScenarioItemRequest request = new ScenarioItemRequest();
        request.setScenarioKey("S1");
        request.setPrimaryMaterialCode("MISSING");
        request.setSubstituteMaterialCode("ALT");

        assertThatThrownBy(() -> bomService.upsertScenarioItem(request))
            .isInstanceOf(BomNotFoundException.class);
    }
}
