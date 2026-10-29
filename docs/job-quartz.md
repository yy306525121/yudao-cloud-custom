# 定时任务部署与验收

聚合服务默认 `yudao.job.type=quartz`，独立服务未设置该属性时仍使用 XXL-Job。修改配置并重启后生效：

- `quartz`：应用内 Quartz，任务通过 `/admin-api/infra/job` 管理。
- `xxl`：原 XXL-Job 执行器，仍受 `xxl.job.enabled` 控制，任务通过原调度中心管理。
- `none`：关闭本 starter 的任务调度；不会关闭 IoT 自有调度器或其他组件自有定时机制。

Quartz 模式不启动 XXL 执行器；原有 `@XxlJob` 任务继续可用，也支持实现 `JobHandler` 的 Spring Bean。处理器名可使用注解值；单注解方法的 Bean 也支持 Bean 名。多租户任务仍按原租户列表执行，某个租户失败会令本次任务失败，重试可能重复执行已成功的租户，业务须保持幂等。

## 数据库准备

默认使用 JDBC 持久化和集群，`spring.quartz.jdbc.initialize-schema=never`，应用不会创建或重建数据库表。

1. 在目标数据库检查 `infra_job`、`infra_job_log`、`QRTZ_*` 是否存在。业务表结构、字典和权限菜单可参照 `sql/<数据库>/ruoyi-vue-pro.sql`；Quartz 表使用 `sql/<数据库>/quartz.sql`。
2. 只对缺失表提取必要建表语句，审阅后由部署人员执行。仓库初始化脚本可能包含 DROP，禁止直接对已有数据库全量执行。
3. 所有同一 Quartz 集群实例使用同一个数据库和 `spring.quartz.scheduler-name`，实例 ID 使用 AUTO。通用调度器默认名称为 `yudaoScheduler`，与 IoT 自有调度器区分。
4. 部署切换前暂停原调度中心相同任务，避免不同实例仍在使用 XXL 导致重复执行。XXL 调度中心的 Cron 和参数不会自动迁移，需要人工核对并录入 `infra_job`。

本改造不新增或自动开启预置任务。`/infra/job/sync` 将业务任务配置同步到 Quartz，保留暂停状态；同步是管理操作，不会在启动时自动调用。已有 Quartz 持久化触发器会按数据库状态恢复。建议首次切换在维护窗口完成同步并逐项核对。

## 管理能力

任务接口前缀 `/infra/job`，提供 create、update、update-status、delete、delete-list、trigger、sync、get、page、export-excel、get_next_times。外部路径沿用应用配置的 `/admin-api` 前缀。权限沿用 `infra:job:*`。

任务处理器名作为 Quartz 唯一标识，创建后不允许修改；更换处理器需删除重建。仅正常状态任务允许编辑。重试间隔、执行耗时单位为毫秒；手动触发不重试。`monitorTimeout` 保留为兼容字段，本次不增加超时中断或告警。

任务日志接口前缀 `/infra/job-log`，支持 get、page、export-excel，沿用 `infra:job:query` 和 `infra:job:export` 权限。清理任务 `jobLogCleanJob` 默认保留 14 天，分批物理删除，每批 100 条；需要自行创建调度记录才会执行。

聚合服务控制台和文件日志输出 MDC `traceId`。独立服务如果启用 Quartz，可在自己的日志格式中加入 `%X{traceId:-}`。

日志清理 SQL 沿用参考项目的 MySQL LIMIT 语法；使用其他数据库时须适配清理语句后再启用清理任务。切回 XXL 时，Quartz 任务管理写操作不可用，已有记录保留供查询。

## 验收

- 单实例：新建安全的演示任务，核对 Cron、暂停/恢复、手动执行、编辑、同步和删除；核对执行日志、重试次数、异常结果与日志中的 traceId。
- 重启：核对任务持久化恢复，暂停任务不自动恢复运行。
- 双实例：相同 scheduler-name、数据库下确认同一触发仅执行一次；单实例下线后确认另一实例接管。
- 模式切换：Quartz 不启动 XXL，xxl 不创建通用 Quartz，none 不创建两者；IoT 调度器独立运行。
- 多租户：核对不同租户的隔离、异常聚合与 traceId；确认后续任务没有残留上下文。

单元测试和编译不代表以上数据库、集群及真实业务任务验收已经完成。
