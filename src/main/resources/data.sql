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
