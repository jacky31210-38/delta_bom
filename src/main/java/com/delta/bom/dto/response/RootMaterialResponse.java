package com.delta.bom.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootMaterialResponse {

    /** 根節點物料編碼（從未被當作子物料使用過的物料） */
    private String materialCode;
    /** 根節點物料名稱 */
    private String materialName;
}
