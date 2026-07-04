package com.delta.bom.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bomOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("BOM 管理系統 API")
                .description("提供 BOM 結構查詢、成本計算、替代料管理功能。\n\n" +
                             "Swagger UI：http://localhost:8095/swagger-ui.html\n" +
                             "H2 Console：http://localhost:8095/h2-console")
                .version("1.0.0"));
    }
}
