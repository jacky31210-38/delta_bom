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

    /** 資料庫自增主鍵 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料編碼，全域唯一 */
    private String materialCode;
    /** 物料名稱 */
    private String materialName;
    /** 單位 */
    private String unit;
    /** 單價，組裝件本身沒有直接單價時可為 null（成本由底下子物料加總算出） */
    private BigDecimal unitPrice;

    /**
     * MyBatis-Plus 樂觀鎖：updateById 時自動對比並遞增 version，防止並發覆寫
     */
    @Version
    private Integer version;

    /** 建立時間，新增時自動填入 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最後更新時間，新增/更新時自動填入 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
