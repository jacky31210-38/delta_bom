package com.delta.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.bom.dto.request.BomComponentCreateRequest;
import com.delta.bom.dto.request.BomComponentUpdateRequest;
import com.delta.bom.dto.response.BomComponentResponse;
import com.delta.bom.dto.response.RootMaterialResponse;
import com.delta.bom.entity.BomComponent;
import com.delta.bom.entity.Material;
import com.delta.bom.exception.BusinessException;
import com.delta.bom.mapper.BomComponentMapper;
import com.delta.bom.mapper.MaterialMapper;
import com.delta.bom.service.BomComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomComponentServiceImpl implements BomComponentService {

    private static final int MAX_VISITED_GUARD = 10_000;

    private final BomComponentMapper bomComponentMapper;
    private final MaterialMapper materialMapper;

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public BomComponentResponse createComponent(BomComponentCreateRequest request) {
        String parentCode = request.getParentMaterialCode();
        String childCode = request.getChildMaterialCode();

        Material parent = getMaterialOrThrow(parentCode);
        Material child = getMaterialOrThrow(childCode);

        BomComponent existing = bomComponentMapper.selectOne(
            new LambdaQueryWrapper<BomComponent>()
                .eq(BomComponent::getParentMaterialCode, parentCode)
                .eq(BomComponent::getChildMaterialCode, childCode)
        );
        if (existing != null) {
            throw new BusinessException(String.format("%s 已經定義過包含 %s，請直接修改數量", parentCode, childCode));
        }

        checkNoCycle(parentCode, childCode);

        BomComponent component = BomComponent.builder()
            .parentMaterialCode(parentCode)
            .childMaterialCode(childCode)
            .quantity(request.getQuantity())
            .build();
        bomComponentMapper.insert(component);
        log.info("新增 BOM 組成：{} 包含 {} × {}", parentCode, childCode, request.getQuantity());

        return toResponse(component, parent, child);
    }

    /**
     * 從 childCode 出發展開它自己的組成（BFS），若展開過程中又繞回 parentCode，
     * 代表加入「parentCode 包含 childCode」這條邊後會形成循環（parentCode 的下游又會包含 parentCode 自己）。
     * 寫入時就直接擋下，不用等查詢/展開 BOM 樹時才發現。
     */
    private void checkNoCycle(String parentCode, String childCode) {
        if (parentCode.equals(childCode)) {
            throw new BusinessException("物料不可以是自己的子件：" + parentCode);
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(childCode);

        int guard = 0;
        while (!queue.isEmpty()) {
            if (guard++ > MAX_VISITED_GUARD) {
                throw new BusinessException("BOM 結構過於龐大，無法驗證循環依賴");
            }
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(parentCode)) {
                throw new BusinessException(String.format(
                    "偵測到循環依賴：%s 已存在於 %s 的下游組成中，無法再讓 %s 包含 %s",
                    parentCode, childCode, parentCode, childCode));
            }
            bomComponentMapper.selectList(
                    new LambdaQueryWrapper<BomComponent>().eq(BomComponent::getParentMaterialCode, current)
                ).forEach(c -> queue.add(c.getChildMaterialCode()));
        }
    }

    @Override
    public List<BomComponentResponse> listAll() {
        List<BomComponent> all = bomComponentMapper.selectList(null);
        Map<String, Material> materialMap = loadMaterialMap(all);
        return all.stream()
            .map(c -> toResponse(c, materialMap.get(c.getParentMaterialCode()), materialMap.get(c.getChildMaterialCode())))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public BomComponentResponse updateComponentQuantity(Long id, BomComponentUpdateRequest request) {
        BomComponent component = bomComponentMapper.selectById(id);
        if (component == null) {
            throw new BusinessException("找不到 BOM 組成關係，ID：" + id);
        }
        component.setQuantity(request.getQuantity());
        bomComponentMapper.updateById(component);
        log.info("更新 BOM 組成數量：id={}，quantity={}", id, request.getQuantity());

        Material parent = getMaterialOrThrow(component.getParentMaterialCode());
        Material child = getMaterialOrThrow(component.getChildMaterialCode());
        return toResponse(component, parent, child);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public void deleteComponent(Long id) {
        BomComponent component = bomComponentMapper.selectById(id);
        if (component == null) {
            throw new BusinessException("找不到 BOM 組成關係，ID：" + id);
        }
        // 只移除這一條「父物料包含子物料」的關係；子物料自己的組成是獨立定義的，不受影響、可繼續被其他地方引用
        bomComponentMapper.deleteById(id);
        log.info("刪除 BOM 組成：{} 不再包含 {}", component.getParentMaterialCode(), component.getChildMaterialCode());
    }

    @Override
    public List<RootMaterialResponse> listRoots() {
        List<BomComponent> all = bomComponentMapper.selectList(null);
        Set<String> parents = all.stream().map(BomComponent::getParentMaterialCode).collect(Collectors.toSet());
        Set<String> children = all.stream().map(BomComponent::getChildMaterialCode).collect(Collectors.toSet());
        parents.removeAll(children);

        if (parents.isEmpty()) {
            return List.of();
        }
        Map<String, Material> materialMap = materialMapper.selectList(
                new LambdaQueryWrapper<Material>().in(Material::getMaterialCode, parents)
            ).stream()
            .collect(Collectors.toMap(Material::getMaterialCode, m -> m));

        return parents.stream()
            .map(code -> {
                Material material = materialMap.get(code);
                return RootMaterialResponse.builder()
                    .materialCode(code)
                    .materialName(material != null ? material.getMaterialName() : null)
                    .build();
            })
            .collect(Collectors.toList());
    }

    private Map<String, Material> loadMaterialMap(List<BomComponent> components) {
        Set<String> codes = components.stream()
            .flatMap(c -> java.util.stream.Stream.of(c.getParentMaterialCode(), c.getChildMaterialCode()))
            .collect(Collectors.toSet());
        if (codes.isEmpty()) {
            return Map.of();
        }
        return materialMapper.selectList(
                new LambdaQueryWrapper<Material>().in(Material::getMaterialCode, codes)
            ).stream()
            .collect(Collectors.toMap(Material::getMaterialCode, m -> m));
    }

    private Material getMaterialOrThrow(String materialCode) {
        Material material = materialMapper.selectOne(
            new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, materialCode)
        );
        if (material == null) {
            throw new BusinessException("找不到物料：" + materialCode);
        }
        return material;
    }

    private BomComponentResponse toResponse(BomComponent component, Material parent, Material child) {
        return BomComponentResponse.builder()
            .id(component.getId())
            .parentMaterialCode(component.getParentMaterialCode())
            .parentMaterialName(parent != null ? parent.getMaterialName() : null)
            .childMaterialCode(component.getChildMaterialCode())
            .childMaterialName(child != null ? child.getMaterialName() : null)
            .quantity(component.getQuantity())
            .createdAt(component.getCreatedAt())
            .updatedAt(component.getUpdatedAt())
            .build();
    }
}
