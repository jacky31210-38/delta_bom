package com.delta.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.bom.dto.request.MaterialRequest;
import com.delta.bom.dto.response.MaterialResponse;
import com.delta.bom.entity.BomComponent;
import com.delta.bom.entity.Material;
import com.delta.bom.entity.SubstituteScenarioItem;
import com.delta.bom.exception.BusinessException;
import com.delta.bom.exception.OptimisticLockConflictException;
import com.delta.bom.mapper.BomComponentMapper;
import com.delta.bom.mapper.MaterialMapper;
import com.delta.bom.mapper.SubstituteScenarioItemMapper;
import com.delta.bom.service.MaterialFinder;
import com.delta.bom.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final BomComponentMapper bomComponentMapper;
    private final SubstituteScenarioItemMapper scenarioItemMapper;
    private final MaterialFinder materialFinder;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public MaterialResponse createMaterial(MaterialRequest request) {
        Material existing = materialMapper.selectOne(
            new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, request.getMaterialCode())
        );
        if (existing != null) {
            throw new BusinessException("物料編碼已存在：" + request.getMaterialCode());
        }

        Material material = Material.builder()
            .materialCode(request.getMaterialCode())
            .materialName(request.getMaterialName())
            .unit(request.getUnit())
            .unitPrice(request.getUnitPrice())
            .build();
        materialMapper.insert(material);
        log.info("新增物料：{}", request.getMaterialCode());
        return toResponse(material);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MaterialResponse> listMaterials() {
        return materialMapper.selectList(null).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public MaterialResponse updateMaterial(String materialCode, MaterialRequest request) {
        Material material = materialFinder.getOrThrow(materialCode);
        if (request.getVersion() == null) {
            throw new BusinessException("更新物料時必須提供 version，避免覆蓋他人異動");
        }
        material.setMaterialName(request.getMaterialName());
        material.setUnit(request.getUnit());
        material.setUnitPrice(request.getUnitPrice());
        material.setVersion(request.getVersion());
        if (materialMapper.updateById(material) == 0) {
            throw new OptimisticLockConflictException("物料 " + materialCode + " 已被其他人修改，請重新整理後再試");
        }
        log.info("更新物料：{}", materialCode);
        return toResponse(material);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMaterial(String materialCode) {
        materialFinder.getOrThrow(materialCode);

        long componentUsage = bomComponentMapper.selectCount(
            new LambdaQueryWrapper<BomComponent>()
                .eq(BomComponent::getParentMaterialCode, materialCode)
                .or()
                .eq(BomComponent::getChildMaterialCode, materialCode));
        long primaryUsage = scenarioItemMapper.selectCount(
            new LambdaQueryWrapper<SubstituteScenarioItem>().eq(SubstituteScenarioItem::getPrimaryCode, materialCode));
        long substituteUsage = scenarioItemMapper.selectCount(
            new LambdaQueryWrapper<SubstituteScenarioItem>().eq(SubstituteScenarioItem::getSubstituteCode, materialCode));

        if (componentUsage > 0 || primaryUsage > 0 || substituteUsage > 0) {
            List<String> usages = new ArrayList<>();
            if (componentUsage > 0) {
                usages.add("BOM 組成關係（" + componentUsage + " 筆）");
            }
            if (primaryUsage > 0) {
                usages.add("替代方案主料規則（" + primaryUsage + " 筆）");
            }
            if (substituteUsage > 0) {
                usages.add("替代方案替代料規則（" + substituteUsage + " 筆）");
            }
            throw new BusinessException("無法刪除物料 " + materialCode + "，仍被以下引用：" + String.join("、", usages));
        }

        materialMapper.delete(new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, materialCode));
        log.info("刪除物料：{}", materialCode);
    }

    /**
     * 把 Material 實體轉成對外的回應格式。
     *
     * @param material 物料實體
     * @return 物料回應內容
     */
    private MaterialResponse toResponse(Material material) {
        return MaterialResponse.builder()
            .id(material.getId())
            .materialCode(material.getMaterialCode())
            .materialName(material.getMaterialName())
            .unit(material.getUnit())
            .unitPrice(material.getUnitPrice())
            .version(material.getVersion())
            .createdAt(material.getCreatedAt())
            .updatedAt(material.getUpdatedAt())
            .build();
    }
}
