INSERT INTO material (material_code, material_name, unit, unit_price) VALUES
('PCB-CONTROL-V2',   '控制器主電路板',       '塊', NULL),
('POWER-MODULE',     '電源模組',             '個', NULL),
('MCU-MODULE',       '主控模組',             '個', NULL),
('COMM-MODULE',      '通訊模組',             '個', NULL),
('DC-DC-CHIP',       'DC-DC轉換晶片',        '個', 12.00),
('CAPACITOR-FILTER', '濾波電容',             '個', 0.80),
('IC-MCU',           '微控制器',             '個', 65.00),
('CRYSTAL',          '晶振',                '個', 8.00),
('RESET-CAP',        '復位電容',             '個', 0.50),
('RS485-IC',         'RS-485通訊晶片',       '個', 6.00),
('CAN-BUS-IC',       'CAN匯流排控制晶片',    '個', 7.00),
('IC-MCU-GD32',      'GD32 微控制器',        '個', 57.00),
('RS485-IC-ALT-A',   'RS-485 晶片（供應商A）', '個', 6.50),
('RS485-IC-ALT-B',   'RS-485 晶片（供應商B）', '個', 6.20);

-- 每個物料的組成只定義一次，被任何引用它的上層共用（例如 POWER-MODULE 若被多個產品使用，
-- 底下的 DC-DC-CHIP / CAPACITOR-FILTER 不需要重複定義）
INSERT INTO bom_component (parent_material_code, child_material_code, quantity) VALUES
('PCB-CONTROL-V2', 'POWER-MODULE', 1),
('PCB-CONTROL-V2', 'MCU-MODULE', 1),
('PCB-CONTROL-V2', 'COMM-MODULE', 2),
('POWER-MODULE', 'DC-DC-CHIP', 1),
('POWER-MODULE', 'CAPACITOR-FILTER', 3),
('MCU-MODULE', 'IC-MCU', 1),
('MCU-MODULE', 'CRYSTAL', 1),
('MCU-MODULE', 'RESET-CAP', 1),
('COMM-MODULE', 'RS485-IC', 4),
('COMM-MODULE', 'CAN-BUS-IC', 1);

INSERT INTO substitute_scenario (scenario_key, scenario_name, description) VALUES
('MULTI_SOURCE', '多供應商策略', 'IC-MCU 缺貨時改用 GD32 替代方案'),
('RS485_SRC_A', 'RS485 供應商 A', 'RS-485 晶片缺貨，供應商 A 可交付一半數量'),
('RS485_SRC_B', 'RS485 供應商 B', 'RS-485 晶片缺貨，供應商 B 可交付另一半數量');

-- 替代料的名稱/單價一律即時查 material 取得（見上方 material 種子資料），這裡不再重複存一份
INSERT INTO substitute_scenario_item (scenario_key, primary_code, substitute_code, reason, substitute_ratio) VALUES
('MULTI_SOURCE', 'IC-MCU', 'IC-MCU-GD32', '多供應商策略', 1),
-- 示範「同一顆主料可被多個方案的規則共同套用」：查詢時分別輸入數量即可（例如各套用 2 顆，合計不超過 RS485-IC 需求量 4 顆）
('RS485_SRC_A', 'RS485-IC', 'RS485-IC-ALT-A', '多供應商策略', 1),
('RS485_SRC_B', 'RS485-IC', 'RS485-IC-ALT-B', '多供應商策略', 1);
