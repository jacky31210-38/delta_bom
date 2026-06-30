DROP TABLE IF EXISTS substitute_material;
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

-- primary_code UNIQUE 確保一顆主料只對應一筆有效替代料
CREATE TABLE substitute_material (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    primary_code     VARCHAR(64)    NOT NULL,
    substitute_code  VARCHAR(64)    NOT NULL,
    substitute_name  VARCHAR(128),
    reason           VARCHAR(256),
    substitute_qty   DECIMAL(12, 4) NOT NULL,
    unit_price       DECIMAL(12, 4) NOT NULL,
    version          INT            DEFAULT 0,
    created_at       DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_substitute_primary_code UNIQUE (primary_code)
);
