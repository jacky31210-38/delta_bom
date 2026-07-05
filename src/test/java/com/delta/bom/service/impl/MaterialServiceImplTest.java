package com.delta.bom.service.impl;

import com.delta.bom.dto.request.MaterialRequest;
import com.delta.bom.dto.response.MaterialResponse;
import com.delta.bom.entity.Material;
import com.delta.bom.exception.BusinessException;
import com.delta.bom.exception.OptimisticLockConflictException;
import com.delta.bom.mapper.BomComponentMapper;
import com.delta.bom.mapper.MaterialMapper;
import com.delta.bom.mapper.SubstituteScenarioItemMapper;
import com.delta.bom.service.MaterialFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 涵蓋物料刪除時最重要的防呆：只要還被 BOM 組成或替代方案規則引用，就不可以刪除。
 */
@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {

    @Mock
    private MaterialMapper materialMapper;
    @Mock
    private BomComponentMapper bomComponentMapper;
    @Mock
    private SubstituteScenarioItemMapper scenarioItemMapper;
    @Mock
    private MaterialFinder materialFinder;

    @InjectMocks
    private MaterialServiceImpl materialService;

    @Test
    void createMaterial_success_whenCodeNotYetUsed() {
        when(materialMapper.selectOne(any())).thenReturn(null);

        MaterialRequest request = new MaterialRequest();
        request.setMaterialCode("IC-MCU");
        request.setMaterialName("微控制器");

        MaterialResponse response = materialService.createMaterial(request);

        assertThat(response.getMaterialCode()).isEqualTo("IC-MCU");
        verify(materialMapper).insert(any(Material.class));
    }

    @Test
    void createMaterial_duplicateCode_throwsBusinessException() {
        when(materialMapper.selectOne(any())).thenReturn(
            Material.builder().materialCode("IC-MCU").materialName("既有物料").build());

        MaterialRequest request = new MaterialRequest();
        request.setMaterialCode("IC-MCU");
        request.setMaterialName("重複物料");

        assertThatThrownBy(() -> materialService.createMaterial(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已存在");
        verify(materialMapper, never()).insert(any(Material.class));
    }

    @Test
    void updateMaterial_success_whenVersionMatches() {
        Material existing = Material.builder().materialCode("IC-MCU").materialName("舊名稱").version(0).build();
        when(materialFinder.getOrThrow("IC-MCU")).thenReturn(existing);
        when(materialMapper.updateById(existing)).thenReturn(1);

        MaterialRequest request = new MaterialRequest();
        request.setMaterialCode("IC-MCU");
        request.setMaterialName("新名稱");
        request.setVersion(0);

        MaterialResponse response = materialService.updateMaterial("IC-MCU", request);

        assertThat(response.getMaterialName()).isEqualTo("新名稱");
    }

    @Test
    void updateMaterial_withoutVersion_throwsBusinessException() {
        when(materialFinder.getOrThrow("IC-MCU")).thenReturn(
            Material.builder().materialCode("IC-MCU").materialName("舊名稱").version(0).build());

        MaterialRequest request = new MaterialRequest();
        request.setMaterialCode("IC-MCU");
        request.setMaterialName("新名稱");

        assertThatThrownBy(() -> materialService.updateMaterial("IC-MCU", request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必須提供 version");
    }

    @Test
    void updateMaterial_versionConflict_throwsOptimisticLockConflictException() {
        Material existing = Material.builder().materialCode("IC-MCU").materialName("舊名稱").version(0).build();
        when(materialFinder.getOrThrow("IC-MCU")).thenReturn(existing);
        when(materialMapper.updateById(existing)).thenReturn(0);

        MaterialRequest request = new MaterialRequest();
        request.setMaterialCode("IC-MCU");
        request.setMaterialName("新名稱");
        request.setVersion(0);

        assertThatThrownBy(() -> materialService.updateMaterial("IC-MCU", request))
            .isInstanceOf(OptimisticLockConflictException.class)
            .hasMessageContaining("已被其他人修改");
    }

    @Test
    void deleteMaterial_blockedByBomComponentUsage_throwsBusinessException() {
        when(materialFinder.getOrThrow("IC-MCU")).thenReturn(
            Material.builder().materialCode("IC-MCU").materialName("微控制器").build());
        when(bomComponentMapper.selectCount(any())).thenReturn(2L);
        when(scenarioItemMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> materialService.deleteMaterial("IC-MCU"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("BOM 組成關係");
        verify(materialMapper, never()).delete(any());
    }

    @Test
    void deleteMaterial_blockedByScenarioUsage_throwsBusinessException() {
        when(materialFinder.getOrThrow("IC-MCU")).thenReturn(
            Material.builder().materialCode("IC-MCU").materialName("微控制器").build());
        when(bomComponentMapper.selectCount(any())).thenReturn(0L);
        // 第一次呼叫查 primaryCode 引用，第二次查 substituteCode 引用
        when(scenarioItemMapper.selectCount(any())).thenReturn(1L).thenReturn(0L);

        assertThatThrownBy(() -> materialService.deleteMaterial("IC-MCU"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("替代方案主料規則");
        verify(materialMapper, never()).delete(any());
    }

    @Test
    void deleteMaterial_success_whenNotReferencedAnywhere() {
        when(materialFinder.getOrThrow("IC-MCU")).thenReturn(
            Material.builder().materialCode("IC-MCU").materialName("微控制器").build());
        when(bomComponentMapper.selectCount(any())).thenReturn(0L);
        when(scenarioItemMapper.selectCount(any())).thenReturn(0L);

        materialService.deleteMaterial("IC-MCU");

        verify(materialMapper).delete(any());
    }
}
