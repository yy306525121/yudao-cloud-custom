# NGIAM CAS SQL 说明

本次最终配置为 `cas_base_url、cas_service、cas_timeout_millis`，公司统一登录类型为 `35`。以下七种数据库的初始化脚本和增量脚本均包含本次改造所需的三个字段、字典和两个 IAM 绑定权限按钮。

| 数据库 | 全新库初始化脚本 | 已有库增量脚本 |
| --- | --- | --- |
| MySQL | `../../sql/mysql/ruoyi-vue-pro.sql` | `../../sql/mysql/upgrade-ngiam-cas.sql` |
| PostgreSQL | `../../sql/postgresql/ruoyi-vue-pro.sql` | `../../sql/postgresql/upgrade-ngiam-cas.sql` |
| KingbaseES | `../../sql/kingbase/ruoyi-vue-pro.sql` | `../../sql/kingbase/upgrade-ngiam-cas.sql` |
| OpenGauss | `../../sql/opengauss/ruoyi-vue-pro.sql` | `../../sql/opengauss/upgrade-ngiam-cas.sql` |
| Oracle | `../../sql/oracle/ruoyi-vue-pro.sql` | `../../sql/oracle/upgrade-ngiam-cas.sql` |
| SQL Server | `../../sql/sqlserver/ruoyi-vue-pro.sql` | `../../sql/sqlserver/upgrade-ngiam-cas.sql` |
| 达梦 | `../../sql/dm/ruoyi-vue-pro-dm8.sql` | `../../sql/dm/upgrade-ngiam-cas.sql` |

DB2 沿用现有 README 的“尚未适配”状态，本次没有新增 DB2 脚本。

## 全新安装

使用对应数据库的全量初始化脚本即可，不再执行增量脚本。初始化脚本新增 IAM 查询、维护两个按钮，放在用户管理菜单（ID 100）下，按钮 ID 为 5100、5101，分别对应：

- `system:ngiam-binding:query`
- `system:ngiam-binding:update`

已保留手工新增的 `system_social_type=35` 字典及已有 CAS 字段。使用序列的数据库已同步调整初始值，避免手工字典 ID 3003 或新增菜单 ID 与下一次生成的 ID 冲突。

## 已有数据库升级

使用对应数据库的 `upgrade-ngiam-cas.sql`，不要重新执行会重建表的全量初始化脚本。

1. 先检查 `system_social_client` 的实际列。脚本开头每个字段使用单独的 ADD 语句：**只执行不存在的字段对应语句，已存在的列跳过**。如果三个字段都存在，直接跳过全部 ADD，执行后面的字典、权限部分即可。
2. 字段注释与数据库原生类型保持一致；字典和权限插入按业务键检查是否存在，不覆盖已有值 `35`，不改动已有 `40`。存在同值但含义不同的 `35` 时，应先核实再执行。
3. 字典及权限使用数据库原生自增/序列分配 ID。PostgreSQL、KingbaseES、OpenGauss、Oracle 包含序列校准，确保手工插入已有 ID 后不发生下一次生成 ID 冲突；在暂停配置写入的维护窗口执行。Oracle 的校准只消耗序列值，不删除数据。
4. 权限按钮依据 `system:user:list` 查找用户管理父菜单，不假定已有库中的菜单 ID。脚本末尾的核对查询应各返回一个字典和两个权限；权限缺失时检查父菜单是否存在。
5. 脚本不自动授权角色、不调整租户套餐。执行后在管理后台为指定管理员配置权限，租户套餐限制菜单时也需纳入这两个按钮。
6. 部署对应前后端后，清理字典缓存或重新登录；在三方应用中选择 `35`，手工填写对接地址、固定回调地址并启用。未填写对接地址的旧应用不支持 IAM 授权/回调登录，已有账号绑定保留。

旧 `cas_login_url、cas_validate_url` 列不再由当前代码使用，增量脚本继续保留它们，不执行删除列或从旧值自动迁移。也不预置内部域名、应用记录、账号绑定或第三方凭证。

`docs/ngiam-cas` 下的早期 MySQL 分步脚本仍可单独使用，但不要与同等内容的 SQL 目录增量脚本重复执行结构变更；以本目录说明和对应数据库实际列为准。

## 验证范围

本次对七份初始化脚本和七份增量脚本进行了静态一致性检查：字段、字段说明、字典唯一性、权限父菜单和 ID、原生序列起始值及非破坏性升级范围。没有连接目标数据库执行这些脚本，不能视为七种数据库的导入或升级验证通过。
