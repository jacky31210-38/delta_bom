package com.delta.bom.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 自定義 Caffeine CacheManager。
     * - maximumSize(500)：每個 cache 最多快取 500 個 key，超過後 LRU 淘汰
     * - expireAfterWrite(5min)：寫入後 5 分鐘過期，防止長期資料不一致
     * - recordStats()：啟用命中率統計，方便監控
     *
     * 替代料套用時，Service 層以 @CacheEvict 主動清除，確保即時一致性。
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("bomStructure", "bomCost");
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
        );
        return manager;
    }
}
