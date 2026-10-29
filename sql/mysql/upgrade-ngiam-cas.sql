-- NGIAM CAS 增量升级：在已有库上执行，不要运行全量初始化脚本。
-- 第 1 部分：仅执行目标库尚不存在的字段对应的 ADD；已存在的字段跳过。
-- 第 2 部分：补字典与权限，按业务键防重复；不会给角色/租户套餐自动授权。
-- 保留旧 cas_login_url/cas_validate_url 列；不迁移旧地址、不修改 type=40 或账号绑定。
-- 升级后请在三方应用中手工填写 cas_base_url，并为管理员授予查询/维护权限。
-- 建议在暂停配置写入的维护窗口执行，尤其是包含序列校准的数据库。

ALTER TABLE system_social_client ADD cas_base_url varchar(2048) NULL COMMENT 'IAM 对接地址';
ALTER TABLE system_social_client ADD cas_service varchar(2048) NULL COMMENT 'CAS 固定回调地址';
ALTER TABLE system_social_client ADD cas_timeout_millis int NULL COMMENT 'CAS 请求超时毫秒';

START TRANSACTION;

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark)
SELECT 35, '公司统一登录', '35', 'system_social_type', 0, '', '', 'NGIAM CAS'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'system_social_type' AND value = '35' AND deleted = 0);

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status, visible, keep_alive, always_show)
SELECT 'IAM 绑定查询', 'system:ngiam-binding:query', 3, 90, parent.id, '', '', 0, 1, 1, 1
FROM (SELECT MIN(id) AS id FROM system_menu WHERE permission = 'system:user:list' AND deleted = 0) parent
WHERE parent.id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'system:ngiam-binding:query' AND deleted = 0);

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status, visible, keep_alive, always_show)
SELECT 'IAM 绑定维护', 'system:ngiam-binding:update', 3, 91, parent.id, '', '', 0, 1, 1, 1
FROM (SELECT MIN(id) AS id FROM system_menu WHERE permission = 'system:user:list' AND deleted = 0) parent
WHERE parent.id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'system:ngiam-binding:update' AND deleted = 0);

COMMIT;

-- 执行后核对；若权限查询无记录，请确认用户管理菜单 system:user:list 存在。
SELECT id, label, value FROM system_dict_data WHERE dict_type = 'system_social_type' AND value = '35' AND deleted = 0;
SELECT id, name, permission, parent_id FROM system_menu WHERE permission IN ('system:ngiam-binding:query', 'system:ngiam-binding:update') AND deleted = 0;
