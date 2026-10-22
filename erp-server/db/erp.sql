USE cas_sso;

-- ERP 条线
INSERT IGNORE INTO t_channel (channel_code, channel_name, status)
VALUES ('ERP', '企业资源计划', 1);

UPDATE t_channel SET channel_name = '企业资源计划', status = 1 WHERE channel_code = 'ERP';

-- 演示用户授予 ERP 条线
INSERT IGNORE INTO t_user_channel (user_id, channel_code, source, status)
VALUES ('alice', 'ERP', 'MANUAL', 1),
       ('bob', 'ERP', 'MANUAL', 1);

UPDATE t_user_channel SET status = 1
WHERE channel_code = 'ERP' AND user_id IN ('alice', 'bob');

-- ERP 菜单表
CREATE TABLE IF NOT EXISTS erp_menu (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id    BIGINT       NOT NULL DEFAULT 0,
    menu_name    VARCHAR(64)  NOT NULL,
    menu_code    VARCHAR(64)  NOT NULL,
    path         VARCHAR(128) NULL,
    icon         VARCHAR(64)  NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    channel_code VARCHAR(32)  NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_erp_menu_code (menu_code)
) ENGINE = InnoDB;

-- 菜单种子数据：channel_code 为 NULL 表示所有条线可见
INSERT IGNORE INTO erp_menu (id, parent_id, menu_name, menu_code, path, icon, sort_order, channel_code, status)
VALUES (1, 0, '首页',         'dashboard',      '/home',            'HomeFilled',    1, NULL,  1),
       (2, 0, '系统管理',     'system',         NULL,               'Setting',       2, NULL,  1),
       (3, 2, '用户管理',     'system-users',   '/system/users',    'User',          1, NULL,  1),
       (4, 2, '菜单管理',     'system-menus',   '/system/menus',    'Menu',          2, NULL,  1),
       (5, 0, '业务示例',     'example',        NULL,               'Goods',         3, NULL,  1),
       (6, 5, '订单示例',     'example-orders', '/example/orders',  'List',          1, NULL,  1),
       (7, 0, '报表中心',     'reports',        '/reports',         'DataAnalysis',  4, NULL,  1),
       (8, 0, 'ERP 专属看板', 'erp-board',      '/erp/board',       'Monitor',       5, 'ERP', 1);

UPDATE erp_menu SET menu_name = '首页',         path = '/home',           icon = 'HomeFilled',   sort_order = 1, channel_code = NULL,  status = 1 WHERE menu_code = 'dashboard';
UPDATE erp_menu SET menu_name = '系统管理',     path = NULL,              icon = 'Setting',      sort_order = 2, channel_code = NULL,  status = 1 WHERE menu_code = 'system';
UPDATE erp_menu SET menu_name = '用户管理',     path = '/system/users',   icon = 'User',         sort_order = 1, channel_code = NULL,  status = 1 WHERE menu_code = 'system-users';
UPDATE erp_menu SET menu_name = '菜单管理',     path = '/system/menus',   icon = 'Menu',         sort_order = 2, channel_code = NULL,  status = 1 WHERE menu_code = 'system-menus';
UPDATE erp_menu SET menu_name = '业务示例',     path = NULL,              icon = 'Goods',        sort_order = 3, channel_code = NULL,  status = 1 WHERE menu_code = 'example';
UPDATE erp_menu SET menu_name = '订单示例',     path = '/example/orders', icon = 'List',         sort_order = 1, channel_code = NULL,  status = 1 WHERE menu_code = 'example-orders';
UPDATE erp_menu SET menu_name = '报表中心',     path = '/reports',        icon = 'DataAnalysis', sort_order = 4, channel_code = NULL,  status = 1 WHERE menu_code = 'reports';
UPDATE erp_menu SET menu_name = 'ERP 专属看板', path = '/erp/board',      icon = 'Monitor',      sort_order = 5, channel_code = 'ERP', status = 1 WHERE menu_code = 'erp-board';