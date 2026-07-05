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
public class ApiResponse<T> {

    /** 業務狀態碼，200 為成功，其餘對應 GlobalExceptionHandler 裡各類例外 */
    private int code;

    /** 訊息，成功固定為 "success"，失敗則為對應的錯誤說明 */
    private String message;

    /** 實際回傳的資料內容，失敗時為 null */
    private T data;

    /** 這個回應產生的時間 */
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code(200)
            .message("success")
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
