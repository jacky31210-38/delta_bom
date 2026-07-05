package com.delta.bom.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新時一定要把 updatedAt 蓋成現在時間，不能用 strictUpdateFill——
        // entity 是從資料庫查出來的，updatedAt 本來就非 null，strict 版本會判定「已有值」而跳過不填
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
    }
}
