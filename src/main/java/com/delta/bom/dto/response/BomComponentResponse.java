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
public class BomComponentResponse {

    /** 資料庫自增主鍵 */
    private Long id;
    /** 父物料編碼 */
    private String parentMaterialCode;
    /** 父物料名稱 */
    private String parentMaterialName;
    /** 子物料編碼 */
    private String childMaterialCode;
    /** 子物料名稱 */
    private String childMaterialName;
    /** 父物料的 BOM 裡，這個子物料的用量 */
    private BigDecimal quantity;
    /** 樂觀鎖版本號，更新時需帶回以偵測並發覆寫 */
    private Integer version;
    /** 建立時間 */
    private LocalDateTime createdAt;
    /** 最後更新時間 */
    private LocalDateTime updatedAt;
}
