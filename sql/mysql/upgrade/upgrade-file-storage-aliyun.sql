-- ====================================================================
-- 基础设施 - 文件管理：新增阿里云 OSS 存储类型（infra_file_storage）
--
-- 说明：脚本可重复执行；已存在 value = 21 的字典项时不会重复插入。
-- 注意：本脚本不写入固定主键，交由 system_dict_data 的 AUTO_INCREMENT 分配，
--      避免与已有字典数据的主键冲突。
-- ====================================================================

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 21, '阿里云 OSS', '21', 'infra_file_storage', 0, 'default', '', '文件存储器 - 阿里云 OSS', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM `system_dict_data`
    WHERE `dict_type` = 'infra_file_storage'
      AND `value` = '21'
);
