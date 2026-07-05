package com.delta.bom.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @Version} 樂觀鎖註解本身不會生效，一定要額外註冊
 * {@link OptimisticLockerInnerInterceptor} 這個攔截器，updateById 才會真的帶上
 * "WHERE version = ?" 並在成功後遞增版本號；否則帶 {@code @Version} 欄位的實體
 * 呼叫 updateById 時，MyBatis-Plus 會丟出 "MP_OPTLOCK_VERSION_ORIGINAL not found"。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
