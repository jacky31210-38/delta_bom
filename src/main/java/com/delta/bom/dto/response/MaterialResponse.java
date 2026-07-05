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
public class MaterialResponse {

    /** 資料庫自增主鍵 */
    private Long id;
    /** 物料編碼，全域唯一 */
    private String materialCode;
    /** 物料名稱 */
    private String materialName;
    /** 單位 */
    private String unit;
    /** 單價，組裝件本身沒有直接單價時為 null */
    private BigDecimal unitPrice;
    /** 樂觀鎖版本號，更新時需帶回以偵測並發覆寫 */
    private Integer version;
    /** 建立時間 */
    private LocalDateTime createdAt;
    /** 最後更新時間 */
    private LocalDateTime updatedAt;
}
