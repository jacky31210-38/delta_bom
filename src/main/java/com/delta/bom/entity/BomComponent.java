package com.delta.bom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BOM 組成關係：描述「parentMaterialCode 的 BOM 裡包含 childMaterialCode，數量多少」，
 * 只定義一次、被誰引用都共用同一份，不代表樹狀圖裡的某個位置。
 * 一棵 BOM 樹是查詢當下從某個 root 物料出發，遞迴展開這些關係算出來的，不是預先存好的樹。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bom_component")
public class BomComponent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String parentMaterialCode;
    private String childMaterialCode;
    private BigDecimal quantity;

    // MyBatis-Plus 樂觀鎖：updateById 時自動對比並遞增 version，防止並發覆寫
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
