# 公司统一登录（NGIAM CAS）

后端位于 `yudao-cloud`，管理端位于同级 `yudao-ui-admin-vue3`。新增第三方类型 `35 / NGIAM_CAS`，复用授权跳转、社交登录、Token、用户信息及权限加载流程。IAM 身份由管理员提前绑定，不自动开户，不按同名本地账号自动关联。

## 部署与配置

七种数据库的完整初始化及增量脚本统一见 `ngiam-cas`。已有库可使用对应 `sql/<数据库>/upgrade-ngiam-cas.sql` 补字段、字典和权限；下述 MySQL 分步脚本与之功能重叠，结构变更不要重复执行。

1. 部署后端前，按当前数据库状态选择升级方式（脚本均只执行一次，本次不会自动执行）：
   - 尚未安装过本功能：执行 `ngiam-cas/upgrade.mysql.sql`，新增 `cas_base_url、cas_service、cas_timeout_millis` 和 `system_social_type=35` 字典。
   - 已执行过早期 CAS 升级、已有独立登录/验票地址字段：仅执行 `ngiam-cas/add-base-url.mysql.sql` 新增 `cas_base_url`，不要再次执行完整升级脚本。
   - 全新安装：初始化 SQL 已包含字段和字典，不再执行上述 ALTER。
   其他数据库按相同字段做对应升级。
2. 执行 `ngiam-cas/permissions.mysql.sql` 增加用户管理下的 IAM 查询、维护权限按钮，然后通过角色管理授权。租户套餐若限制菜单，也需要纳入相应权限。
3. 部署后端和管理端。字典已被浏览器缓存时重新登录或清理字典缓存，确认能看到“公司统一登录”。
4. 在“系统管理 → 三方登录 → 三方应用”创建应用，选择“公司统一登录”，用户类型为管理端：

| 页面字段 | 后端字段 | 内容 |
| --- | --- | --- |
| 应用编码（可选） | clientId | 可填写 `ljsc` 作记录；CAS 登录不使用此字段 |
| 对接地址 | casBaseUrl | 例如 `http://10.1.102.64:80`；仅填写协议、主机及可选端口，允许末尾 `/` |
| 固定回调地址 | casService | IAM 注册的前端地址，可以是站点首页或其他管理端路径，必须与浏览器访问站点同源 |
| 请求超时(ms) | casTimeoutMillis | 默认 5000，允许 100–60000 |
| 状态 | status | 开启或关闭当前租户的公司统一登录 |

CAS 不需要 appId、客户端密钥或 agentId；应用编码可不填，未填写的客户端编号及密钥在后端保存为空字符串以兼容表约束。应用编码 `ljsc` 只作记录，不作为 appId 传递；文档中的 appCode 属于退出接口参数，本期不接统一退出。对接地址不接受路径（根路径 `/` 除外）、查询参数、片段或用户名密码；请求超时和固定回调地址仍单独配置。固定回调地址不要包含 `ticket、params、accountId、callbackUrl`，不要包含 `#`。地址中的末尾斜杠和静态查询参数必须与 IAM 注册值一致。

**配置只从当前租户的三方应用数据库记录读取，保存后生效，没有 `yudao.ngiam-cas` 或环境变量兜底。** 升级前曾设置的 `NGIAM_CAS_*` 环境变量应移除，不会自动迁移为数据库记录。原有 `justauth.enabled` 仍需保持开启。

禁用或删除应用后，新的授权和回调登录都会被拒绝，登录页重新加载配置后隐藏按钮。修改关键配置会使先前发起的登录状态失效，用户应重新发起登录。关闭 IAM 不删除绑定，也不撤销系统已签发的 Token。

**已有配置需要管理员手工重新填写对接地址并保存，不从旧登录/验票地址自动提取。** 未填写时登录页不显示公司统一登录按钮，后端也不接受该方式的授权或回调登录；账号绑定保留。旧 `cas_login_url、cas_validate_url` 数据库列不删除，但不再被接口、表单或运行逻辑读取。

后端固定拼接以下两个路径，不允许在后台分别修改：

```text
http://10.1.102.64:80/ngiam-rst/cas/login
http://10.1.102.64:80/ngiam-rst/cas/serviceValidate
```

登录时附加编码后的 `service、params`；验票时附加 `ticket、service、format=json`。填写对接地址时不要粘贴上述路径。回调地址仍需在 IAM 注册，授权与验票使用相同 service。修改对接地址会使先前发起的登录状态失效。

需要单独添加字典时使用 `dictionary.mysql.sql`，原有字典值 `40` 保留；脚本不修改任何已有三方应用或账号绑定数据。

## 管理员预绑定

用户管理列表 → 更多 → IAM 绑定，填写 **IAM 账户名**，精确保留大小写与字符。回调 URL 中的 `accountId` 不是绑定字段，也不能作为认证依据。账户名以服务端验票成功返回的 `user` 为准。

同一租户内，一个 IAM 账户只能绑定一个本地用户，一个本地用户只能绑定一个 IAM 账户；重复提交同一绑定幂等，换绑须显式解绑。原有社交自助绑定/解绑接口拒绝 CAS，个人中心不新增 IAM 自助入口。

| 方法 | 路径（加 `/admin-api`） | 参数 | 权限 |
| --- | --- | --- | --- |
| GET | `/system/ngiam-binding/get` | 查询参数 userId | system:ngiam-binding:query |
| POST | `/system/ngiam-binding/bind` | JSON `{userId,iamUsername}` | system:ngiam-binding:update |
| DELETE | `/system/ngiam-binding/unbind` | 查询参数 userId | system:ngiam-binding:update |

复用 `system_social_user`、`system_social_user_bind`；按租户使用 Redisson 锁串行执行绑定写入，事务提交后释放锁。IAM 账户名采用 Java 精确比较，不依赖数据库排序规则。现有 openid/nickname 初始化字段长度为 32 字符，输入同步限制为 32 字符；需要更长账户名时须先核对实际库字段并同步扩展。

## 登录链路

1. 登录页识别租户后调用公开接口 `GET /admin-api/system/auth/ngiam-config`。仅返回 `{enabled,service}`，不暴露验票地址、后台配置或其他平台密钥。
2. 用户点击“公司统一登录”，调用现有 `GET /admin-api/system/auth/social-auth-redirect`，参数 `type=35、redirectUri=<固定回调地址>、returnPath=<原站内目标路径>`。`returnPath` 默认 `/`。
3. 后端读取当前租户配置，生成 `{casBaseUrl}/ngiam-rst/cas/login?service=<编码后的固定回调地址>&params=<随机 state>`；Redis 保存原始目标路径、service 和配置指纹，有效期 5 分钟。
4. 前端跳转前检查授权地址：缺失或为空的 `service` 用当前三方应用的固定回调地址补齐，存在不同 `service` 时停止跳转并提示配置不一致。移除 IAM 内部页面使用的 appId、loginType，保留 params，通过 URLSearchParams 编码回调地址。随后将 state、租户、固定回调地址及时间保存到当前标签页的 sessionStorage，再跳转 IAM。既有微信、钉钉授权 URL 编码逻辑不变。
5. IAM 回跳携带 `ticket、params`。路由守卫在普通认证跳转前识别回调，包括首页，核对本标签页的 state、租户、时效和回调地址，立即从地址栏清除回调参数并消费本地状态，再转入独立的 CAS 处理页面。
6. 回调页面提交 `POST /admin-api/system/auth/social-login`：`{type:35,code:<ticket>,state:<params>}`。后端按租户原子消费 Redis 状态，核对当前配置指纹，再用相同 service 调用 CAS 的 serviceValidate 验票。
7. 后端以验票返回的账户名查找绑定，检查本地账号存在且启用，再签发系统 Token。前端清理旧用户信息和动态路由缓存，保存 Token，重新加载字典、用户及权限路由，以 replace 跳转返回的 redirectPath，保留目标页查询参数。

回调参数只通过 URLSearchParams 解析一次；不信任 accountId 或 callbackUrl，不使用旧的 code/state 数据库缓存。缺失状态、过期、租户变化、票据无效或未绑定时明确提示并提供“重新登录”，不自动重试单次票据，不进入账号密码自助绑定页面。IAM 平台直接回跳、但没有从本应用发起过登录的链接会被拒绝。

目标路径须以单个 `/` 开头，拒绝外部地址、反斜杠、控制字符和预编码 `%` 内容；接口查询参数正常 URL 编码不受影响。统一退出不在本次范围。

## 验证

后端针对性检查包含：三方应用 CAS 字段持久化与更新、条件校验、公开配置与启停、配置指纹变更、租户状态隔离、验票、防重放、绑定冲突/幂等/解绑和本地禁用账号，并回归原社交服务测试。

前端运行：

```sh
node --test tests/ngiam-cas.test.mjs
pnpm ts:check
pnpm build:prod
```

回调测试使用 Node 内置测试工具和项目已有 TypeScript，覆盖首页与其他回调路径、静态查询参数、缺失/重复/不匹配 state、过期、租户变化及站内跳转校验。

数据库升级未自动执行，真实 IAM 的对接地址、注册 service 和成功响应需要在实际环境确认。本地单测、模拟接口浏览器验证或构建通过，均不等同真实 IAM、Redis、数据库和生产登录联调通过。
