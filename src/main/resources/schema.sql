DROP TABLE IF EXISTS substitute_scenario_item;
DROP TABLE IF EXISTS substitute_scenario;
DROP TABLE IF EXISTS bom_item;

CREATE TABLE bom_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_code   VARCHAR(64)    NOT NULL,
    item_name   VARCHAR(128)   NOT NULL,
    unit        VARCHAR(16),
    quantity    DECIMAL(12, 4) NOT NULL DEFAULT 1,
    unit_price  DECIMAL(12, 4),
    level       INT            NOT NULL DEFAULT 0,
    parent_code VARCHAR(64),
    created_at  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bom_item_code UNIQUE (item_code)
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
-- 只描述「哪個料可以換哪個料、換算比例與單價」的工程事實，不記錄數量——
-- 數量是查詢/計算成本當下才決定的動態輸入（同一顆主料可被多個方案的規則同時套用不同數量）。
-- substitute_ratio：替代比例，1 顆主料對應幾顆替代料（預設 1，即 1:1 替換）。
CREATE TABLE substitute_scenario_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario_key    VARCHAR(64)    NOT NULL,
    primary_code    VARCHAR(64)    NOT NULL,
    substitute_code VARCHAR(64)    NOT NULL,
    substitute_name VARCHAR(128),
    reason          VARCHAR(256),
    substitute_ratio DECIMAL(12, 4) NOT NULL DEFAULT 1,
    unit_price      DECIMAL(12, 4) NOT NULL,
    version         INT            DEFAULT 0,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_scenario_primary UNIQUE (scenario_key, primary_code),
    CONSTRAINT fk_item_scenario FOREIGN KEY (scenario_key)
        REFERENCES substitute_scenario (scenario_key)
);
