package com.delta.bom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioResponse {

    /** 資料庫自增主鍵 */
    private Long id;
    /** 方案代碼 */
    private String scenarioKey;
    /** 方案顯示名稱 */
    private String scenarioName;
    /** 方案描述 */
    private String description;
    /** 這個方案底下目前有幾筆替代規則 */
    private long itemCount;
    /** 樂觀鎖版本號（此方案目前僅有新增/刪除，無更新功能，這裡純資訊揭露） */
    private Integer version;
    /** 建立時間 */
    private LocalDateTime createdAt;
    /** 最後更新時間 */
    private LocalDateTime updatedAt;
}
