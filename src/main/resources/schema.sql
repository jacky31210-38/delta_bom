DROP TABLE IF EXISTS substitute_scenario_item;
DROP TABLE IF EXISTS substitute_scenario;
DROP TABLE IF EXISTS bom_component;
DROP TABLE IF EXISTS material;

-- 物料主檔：純粹描述「這個物料是什麼」，不含任何 BOM 組成資訊
CREATE TABLE material (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_code VARCHAR(64)    NOT NULL,
    material_name VARCHAR(128)   NOT NULL,
    unit          VARCHAR(16),
    unit_price    DECIMAL(12, 4),
    version       INT      DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_material_code UNIQUE (material_code)
);

-- BOM 組成：描述「物料對物料」的組成關係（parent_material_code 的 BOM 裡包含 child_material_code），
-- 只定義一次、被誰引用都共用同一份，不是「樹狀圖裡的某個位置」。
-- 一棵 BOM 樹是查詢當下從某個 root 物料出發，遞迴展開這些關係算出來的，不是預先存好的樹。
CREATE TABLE bom_component (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_material_code  VARCHAR(64)    NOT NULL,
    child_material_code   VARCHAR(64)    NOT NULL,
    quantity              DECIMAL(12, 4) NOT NULL DEFAULT 1,
    version               INT            DEFAULT 0,
    created_at            DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bom_component UNIQUE (parent_material_code, child_material_code),
    CONSTRAINT fk_component_parent FOREIGN KEY (parent_material_code) REFERENCES material (material_code),
    CONSTRAINT fk_component_child FOREIGN KEY (child_material_code) REFERENCES material (material_code)
);

-- 替代方案主表：每筆代表一個可命名的替代料組合（例如「多供應商策略」）
CREATE TABLE substitute_scenario (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario_key  VARCHAR(64)    NOT NULL,
    scenario_name VARCHAR(128)   NOT NULL,
    description   VARCHAR(256),
    version       INT            DEFAULT 0,
    created_at    DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_scenario_key UNIQUE (scenario_key)
);

-- 替代方案明細：同一方案內，一顆主料只能對應一筆替代規則。
-- 只描述「哪個料可以換哪個料、換算比例」這種替代關係本身的事實，不記錄數量——
-- 數量是查詢/計算成本當下才決定的動態輸入（同一顆主料可被多個方案的規則同時套用不同數量）。
-- primary_code / substitute_code 皆為 FK 指向 material，兩者的名稱與單價一律即時查 material 取得，
-- 不在這裡另存一份，避免物料主檔改價後，方案明細裡的舊資料沒有同步更新。
-- substitute_ratio：替代比例，1 顆主料對應幾顆替代料（預設 1，即 1:1 替換）。
CREATE TABLE substitute_scenario_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario_key    VARCHAR(64)    NOT NULL,
    primary_code    VARCHAR(64)    NOT NULL,
    substitute_code VARCHAR(64)    NOT NULL,
    reason          VARCHAR(256),
    substitute_ratio DECIMAL(12, 4) NOT NULL DEFAULT 1,
    version         INT            DEFAULT 0,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_scenario_primary UNIQUE (scenario_key, primary_code),
    CONSTRAINT fk_item_scenario FOREIGN KEY (scenario_key)
        REFERENCES substitute_scenario (scenario_key),
    CONSTRAINT fk_item_primary_material FOREIGN KEY (primary_code)
        REFERENCES material (material_code),
    CONSTRAINT fk_item_substitute_material FOREIGN KEY (substitute_code)
        REFERENCES material (material_code)
);
