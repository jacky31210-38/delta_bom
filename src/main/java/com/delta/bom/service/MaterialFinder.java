package com.delta.bom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.bom.entity.Material;
import com.delta.bom.exception.BomNotFoundException;
import com.delta.bom.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 「依物料編碼查找，查無則丟例外」是 Material/BomComponent/Bom 三個 Service 都要做的事，
 * 集中在這裡實作一次，避免各自重複、日後各自維護出不一致的例外型別。
 */
@Component
@RequiredArgsConstructor
public class MaterialFinder {

    private final MaterialMapper materialMapper;

    public Material getOrThrow(String materialCode) {
        Material material = materialMapper.selectOne(
            new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, materialCode)
        );
        if (material == null) {
            throw new BomNotFoundException(materialCode);
        }
        return material;
    }
}
