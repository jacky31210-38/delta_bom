package com.delta.bom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioItemResponse {

    /** 資料庫自增主鍵 */
    private Long id;
    /** 所屬方案代碼 */
    private String scenarioKey;
    /** 主料編碼（要被替換的物料） */
    private String primaryCode;
    /** 替代料編碼 */
    private String substituteCode;
    /** 替代料名稱，即時從物料主檔查得 */
    private String substituteName;
    /** 替代原因，選填 */
    private String reason;
    /** 替代比例：1 顆主料對應幾顆替代料 */
    private BigDecimal substituteRatio;
    /** 替代料單價，即時從物料主檔查得 */
    private BigDecimal unitPrice;
    /** 樂觀鎖版本號，更新時需帶回以偵測並發覆寫 */
    private Integer version;
    /** 建立時間 */
    private LocalDateTime createdAt;
    /** 最後更新時間 */
    private LocalDateTime updatedAt;
}
