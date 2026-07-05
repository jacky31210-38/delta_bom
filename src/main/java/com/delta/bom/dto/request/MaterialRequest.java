package com.delta.bom.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialRequest {

    /** 物料編碼，全域唯一 */
    @NotBlank(message = "物料編碼不可為空")
    private String materialCode;

    /** 物料名稱 */
    @NotBlank(message = "物料名稱不可為空")
    private String materialName;

    /** 單位，選填 */
    private String unit;

    /** 單價，組裝件本身可留空 */
    @DecimalMin(value = "0.0", inclusive = false, message = "單價必須大於 0")
    private BigDecimal unitPrice;

    /** 更新時必填：前端讀取資料當下拿到的版本號，用來偵測並發覆寫；新增時不需要，可留空 */
    private Integer version;
}
