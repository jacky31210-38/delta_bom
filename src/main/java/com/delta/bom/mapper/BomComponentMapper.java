package com.delta.bom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delta.bom.entity.BomComponent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BomComponentMapper extends BaseMapper<BomComponent> {

    /**
     * 以遞迴 CTE 從指定根物料出發，展開整棵 BOM 樹涉及的所有「父物料→子物料」邊。
     * SQL 定義於 BomComponentMapper.xml。
     */
    List<BomComponent> selectSubtreeEdges(@Param("rootMaterialCode") String rootMaterialCode);
}
