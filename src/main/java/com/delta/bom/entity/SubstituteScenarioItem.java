package com.delta.bom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("substitute_scenario_item")
public class SubstituteScenarioItem {

    /** 資料庫自增主鍵 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所屬方案代碼 */
    private String scenarioKey;
    /** 主料編碼（要被替換的物料） */
    private String primaryCode;
    /** 替代料編碼 */
    private String substituteCode;
    /** 替代原因，選填 */
    private String reason;
    /** 替代比例：1 顆主料對應幾顆替代料，預設 1（1:1 替換） */
    private BigDecimal substituteRatio;

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
