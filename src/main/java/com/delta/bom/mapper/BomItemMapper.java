package com.delta.bom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delta.bom.entity.BomItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BomItemMapper extends BaseMapper<BomItem> {

    /**
     * 以遞迴 CTE 查詢指定根節點的完整子樹（含根節點自身）。
     * SQL 定義於 BomItemMapper.xml。
     */
    List<BomItem> selectDescendantsWithSelf(@Param("rootCode") String rootCode);
}
