package com.delta.bom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("substitute_scenario")
public class SubstituteScenario {

    /** 資料庫自增主鍵 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案代碼，全域唯一，作為方案的識別鍵 */
    private String scenarioKey;
    /** 方案顯示名稱 */
    private String scenarioName;
    /** 方案描述，選填 */
    private String description;

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
