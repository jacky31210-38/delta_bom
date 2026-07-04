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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scenarioKey;
    private String scenarioName;
    private String description;

    // MyBatis-Plus 樂觀鎖：updateById 時自動對比並遞增 version，防止並發覆寫
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
