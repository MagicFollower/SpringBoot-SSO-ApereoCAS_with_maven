USE cas_sso;

-- 演示条线
INSERT IGNORE INTO t_channel (channel_code, channel_name, status)
VALUES ('RETAIL', '零售条线', 1),
       ('CREDIT', '信贷条线', 1);

-- 演示用户，密码均为 Passw0rd!（BCrypt）
INSERT IGNORE INTO t_user (username, password_hash, display_name, dept_id, enabled)
VALUES ('alice', '$2a$10$q2urDFIXedECAz3hgGdGbuV647K3J6fqgxQU9Kp11ucM5wSUu741K', 'Alice', 'D001', 1),
       ('bob',   '$2a$10$kO9JHjUMetT.a8uqfPN5FuXtefr0oJ6qS.mXwsVWwD39UO3/35g2S', 'Bob',   'D002', 1);

-- alice 只能进零售条线；bob 同时拥有零售和信贷
INSERT IGNORE INTO t_user_channel (user_id, channel_code, source, status)
VALUES ('alice', 'RETAIL', 'MANUAL', 1),
       ('bob',   'RETAIL', 'MANUAL', 1),
       ('bob',   'CREDIT', 'MANUAL', 1);
