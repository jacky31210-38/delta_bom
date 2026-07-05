package com.delta.bom.service.impl;

import com.delta.bom.dto.request.BomComponentCreateRequest;
import com.delta.bom.dto.request.BomComponentUpdateRequest;
import com.delta.bom.dto.response.BomComponentResponse;
import com.delta.bom.dto.response.RootMaterialResponse;
import com.delta.bom.entity.BomComponent;
import com.delta.bom.entity.Material;
import com.delta.bom.exception.BomNotFoundException;
import com.delta.bom.exception.BusinessException;
import com.delta.bom.exception.OptimisticLockConflictException;
import com.delta.bom.mapper.BomComponentMapper;
import com.delta.bom.mapper.MaterialMapper;
import com.delta.bom.service.MaterialFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 涵蓋 BOM 組成關係新增時最容易出錯的部分：自我循環、多層循環依賴、重複邊。
 * 這三個規則都是寫入時就要擋下的防禦性驗證，值得直接測，而不是等到查詢時才發現資料壞掉。
 */
@ExtendWith(MockitoExtension.class)
class BomComponentServiceImplTest {

    @Mock
    private BomComponentMapper bomComponentMapper;
    @Mock
    private MaterialMapper materialMapper;
    @Mock
    private MaterialFinder materialFinder;

    @InjectMocks
    private BomComponentServiceImpl bomComponentService;

    @Test
    void createComponent_success_whenNoConflict() {
        Material parent = Material.builder().materialCode("A").materialName("A").build();
        Material child = Material.builder().materialCode("B").materialName("B").build();
        when(materialFinder.getOrThrow("A")).thenReturn(parent);
        when(materialFinder.getOrThrow("B")).thenReturn(child);
        when(bomComponentMapper.selectOne(any())).thenReturn(null); // 尚未定義過這條邊
        when(bomComponentMapper.selectList(any())).thenReturn(List.of()); // B 底下沒有子件，不會形成循環

        BomComponentCreateRequest request = new BomComponentCreateRequest();
        request.setParentMaterialCode("A");
        request.setChildMaterialCode("B");
        request.setQuantity(new BigDecimal("2"));

        BomComponentResponse response = bomComponentService.createComponent(request);

        assertThat(response.getParentMaterialCode()).isEqualTo("A");
        assertThat(response.getChildMaterialCode()).isEqualTo("B");
    }

    @Test
    void createComponent_selfLoop_throwsBusinessException() {
        when(materialFinder.getOrThrow("A")).thenReturn(Material.builder().materialCode("A").materialName("A").build());

        BomComponentCreateRequest request = new BomComponentCreateRequest();
        request.setParentMaterialCode("A");
        request.setChildMaterialCode("A");
        request.setQuantity(BigDecimal.ONE);

        assertThatThrownBy(() -> bomComponentService.createComponent(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不可以是自己的子件");
    }

    @Test
    void createComponent_wouldFormCycle_throwsBusinessException() {
        // A 已經是 B 的下游（B 包含 A），此時若再讓 A 包含 B，會形成循環
        Material a = Material.builder().materialCode("A").materialName("A").build();
        Material b = Material.builder().materialCode("B").materialName("B").build();
        when(materialFinder.getOrThrow("A")).thenReturn(a);
        when(materialFinder.getOrThrow("B")).thenReturn(b);
        when(bomComponentMapper.selectOne(any())).thenReturn(null);

        BomComponent bContainsA = BomComponent.builder()
            .parentMaterialCode("B").childMaterialCode("A").quantity(BigDecimal.ONE).build();
        // checkNoCycle 從 childCode="B" 出發展開它自己的組成；B 的組成裡有 A，代表 A 已存在於 B 的下游
        when(bomComponentMapper.selectList(any())).thenReturn(List.of(bContainsA));

        BomComponentCreateRequest request = new BomComponentCreateRequest();
        request.setParentMaterialCode("A");
        request.setChildMaterialCode("B");
        request.setQuantity(BigDecimal.ONE);

        assertThatThrownBy(() -> bomComponentService.createComponent(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("循環依賴");
    }

    @Test
    void createComponent_duplicateEdge_throwsBusinessException() {
        when(materialFinder.getOrThrow("A")).thenReturn(Material.builder().materialCode("A").materialName("A").build());
        when(materialFinder.getOrThrow("B")).thenReturn(Material.builder().materialCode("B").materialName("B").build());
        when(bomComponentMapper.selectOne(any())).thenReturn(
            BomComponent.builder().parentMaterialCode("A").childMaterialCode("B").quantity(BigDecimal.ONE).build());

        BomComponentCreateRequest request = new BomComponentCreateRequest();
        request.setParentMaterialCode("A");
        request.setChildMaterialCode("B");
        request.setQuantity(new BigDecimal("3"));

        assertThatThrownBy(() -> bomComponentService.createComponent(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("請直接修改數量");
    }

    @Test
    void createComponent_parentMaterialNotFound_throwsBomNotFoundException() {
        when(materialFinder.getOrThrow("MISSING")).thenThrow(new BomNotFoundException("MISSING"));

        BomComponentCreateRequest request = new BomComponentCreateRequest();
        request.setParentMaterialCode("MISSING");
        request.setChildMaterialCode("B");
        request.setQuantity(BigDecimal.ONE);

        assertThatThrownBy(() -> bomComponentService.createComponent(request))
            .isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void updateComponentQuantity_success_updatesQuantityAndReturnsResponse() {
        BomComponent existing = BomComponent.builder()
            .id(1L).parentMaterialCode("A").childMaterialCode("B")
            .quantity(BigDecimal.ONE).version(0).build();
        when(bomComponentMapper.selectById(1L)).thenReturn(existing);
        when(bomComponentMapper.updateById(existing)).thenReturn(1);
        when(materialFinder.getOrThrow("A")).thenReturn(Material.builder().materialCode("A").materialName("A").build());
        when(materialFinder.getOrThrow("B")).thenReturn(Material.builder().materialCode("B").materialName("B").build());

        BomComponentUpdateRequest request = new BomComponentUpdateRequest();
        request.setQuantity(new BigDecimal("5"));
        request.setVersion(0);

        BomComponentResponse response = bomComponentService.updateComponentQuantity(1L, request);

        assertThat(response.getQuantity()).isEqualByComparingTo("5");
        verify(bomComponentMapper).updateById(existing);
    }

    @Test
    void updateComponentQuantity_versionConflict_throwsOptimisticLockConflictException() {
        BomComponent existing = BomComponent.builder()
            .id(1L).parentMaterialCode("A").childMaterialCode("B")
            .quantity(BigDecimal.ONE).version(0).build();
        when(bomComponentMapper.selectById(1L)).thenReturn(existing);
        // 模擬別人已經搶先更新過，version 已經不是 0 了：WHERE version = 0 比對不到任何一列，回傳受影響筆數 0
        when(bomComponentMapper.updateById(existing)).thenReturn(0);

        BomComponentUpdateRequest request = new BomComponentUpdateRequest();
        request.setQuantity(new BigDecimal("5"));
        request.setVersion(0);

        assertThatThrownBy(() -> bomComponentService.updateComponentQuantity(1L, request))
            .isInstanceOf(OptimisticLockConflictException.class)
            .hasMessageContaining("已被其他人修改");
    }

    @Test
    void listRoots_returnsOnlyMaterialsNeverUsedAsChild() {
        BomComponent rootToChild = BomComponent.builder()
            .parentMaterialCode("ROOT").childMaterialCode("CHILD").quantity(BigDecimal.ONE).build();
        when(bomComponentMapper.selectList(any())).thenReturn(List.of(rootToChild));
        when(materialMapper.selectList(any())).thenReturn(
            List.of(Material.builder().materialCode("ROOT").materialName("Root").build()));

        List<RootMaterialResponse> roots = bomComponentService.listRoots();

        assertThat(roots).extracting(RootMaterialResponse::getMaterialCode).containsExactly("ROOT");
    }
}
