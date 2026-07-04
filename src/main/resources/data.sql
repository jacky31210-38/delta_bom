INSERT INTO bom_item (item_code, item_name, unit, quantity, unit_price, level, parent_code) VALUES
('PCB-CONTROL-V2',   '控制器主電路板',       '塊', 1,    NULL,  0, NULL),
('POWER-MODULE',     '電源模組',             '個', 1,    NULL,  1, 'PCB-CONTROL-V2'),
('MCU-MODULE',       '主控模組',             '個', 1,    NULL,  1, 'PCB-CONTROL-V2'),
('COMM-MODULE',      '通訊模組',             '個', 2,    NULL,  1, 'PCB-CONTROL-V2'),
('DC-DC-CHIP',       'DC-DC轉換晶片',        '個', 1,   12.00,  2, 'POWER-MODULE'),
('CAPACITOR-FILTER', '濾波電容',             '個', 3,    0.80,  2, 'POWER-MODULE'),
('IC-MCU',           '微控制器',             '個', 1,   65.00,  2, 'MCU-MODULE'),
('CRYSTAL',          '晶振',                '個', 1,    8.00,  2, 'MCU-MODULE'),
('RESET-CAP',        '復位電容',             '個', 1,    0.50,  2, 'MCU-MODULE'),
('RS485-IC',         'RS-485通訊晶片',       '個', 4,    6.00,  2, 'COMM-MODULE'),
('CAN-BUS-IC',       'CAN匯流排控制晶片',    '個', 1,    7.00,  2, 'COMM-MODULE');

INSERT INTO substitute_scenario (scenario_key, scenario_name, description) VALUES
('MULTI_SOURCE', '多供應商策略', 'IC-MCU 缺貨時改用 GD32 替代方案'),
('RS485_SRC_A', 'RS485 供應商 A', 'RS-485 晶片缺貨，供應商 A 可交付一半數量'),
('RS485_SRC_B', 'RS485 供應商 B', 'RS-485 晶片缺貨，供應商 B 可交付另一半數量');

INSERT INTO substitute_scenario_item (scenario_key, primary_code, substitute_code, substitute_name, reason, substitute_ratio, unit_price) VALUES
('MULTI_SOURCE', 'IC-MCU', 'IC-MCU-GD32', 'GD32 微控制器', '多供應商策略', 1, 57.00),
-- 示範「同一顆主料可被多個方案的規則共同套用」：查詢時分別輸入數量即可（例如各套用 2 顆，合計不超過 RS485-IC 需求量 4 顆）
('RS485_SRC_A', 'RS485-IC', 'RS485-IC-ALT-A', 'RS-485 晶片（供應商A）', '多供應商策略', 1, 6.50),
('RS485_SRC_B', 'RS485-IC', 'RS485-IC-ALT-B', 'RS-485 晶片（供應商B）', '多供應商策略', 1, 6.20);
