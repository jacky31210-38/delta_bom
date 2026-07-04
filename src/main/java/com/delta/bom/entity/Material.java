package com.delta.bom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("material")
public class Material {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;
    private String materialName;
    private String unit;
    private BigDecimal unitPrice;

    // MyBatis-Plus 樂觀鎖：updateById 時自動對比並遞增 version，防止並發覆寫
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
