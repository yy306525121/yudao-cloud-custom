# 跨域白名单 yudao.web.cors.allowed-origins

网关和所有后端应用共用同一份 `CorsProperties`，读取 `yudao.web.cors.allowed-origins`。默认空列表沿用原有"放行所有来源"的行为；一旦填写，只精确匹配完整 Origin，**不支持通配符**，非法值直接导致应用启动失败。**修改后必须重启，配置不支持热更新。**

## 配置项

| 键 | 类型 | 默认值 | 作用范围 | 生效方式 |
| --- | --- | --- | --- | --- |
| `yudao.web.cors.allowed-origins` | `List<String>` | `[]` | 网关 + 每个后端应用 | 重启 |

```yaml
yudao:
  web:
    cors:
      # 空列表兼容放行；非空时精确匹配协议、域名/IP、端口，不支持通配符。修改后重启生效。
      # allowed-origins: ["https://admin.example.com", "http://localhost:3000"]
      allowed-origins: []
```

允许的请求头和请求方法固定为 `*`，跨域规则注册在 `/**`，均不可配置。

## 匹配规则

每项必须是完整 Origin：协议 + 主机 + 可选端口，例如 `https://admin.example.com`、`http://localhost:3000`、`http://10.1.102.64:8080`。

- 协议、主机、端口三者都要一致：配置 `https://ui.example.com` 时，`http://ui.example.com` 和 `https://ui.example.com:8443` 都不匹配。
- 默认端口不要写，浏览器不会在 `Origin` 请求头里带 `:443` 或 `:80`。
- 不接受路径、末尾 `/` 和查询串，`https://ui.example.com/` 不会命中。
- **不接受任何含 `*` 的值**，`https://*.example.com`、`*` 都会在启动时报错。
- 不接受 null 项和空白项。

绑定阶段校验失败时应用启动中断，异常信息为 `yudao.web.cors.allowed-origins 必须为完整来源列表，不允许空白或通配符`。跨域请求的 Origin 不在白名单时返回 **403，请求不会转发到后端**；同源请求和未带 `Origin` 的请求不受影响。

同源判定比较协议、主机和端口（`80` / `443` 自动归一），所以前端与后端同源时不必把该域名写进白名单。但该判定忽略 `X-Forwarded-*`：若由 Nginx 终止 HTTPS 而后端只看到 `http`，浏览器发来的 `https://` Origin 会被判为跨域，此时**必须把前端域名写入白名单**，或在应用前启用 `ForwardedHeaderFilter` 还原真实协议。

## 配置位置

网关与每个后端应用各自独立绑定该配置，**必须填写相同的白名单**。只配网关会出现"网关放行、后端 403"；只配后端则请求在网关就被拦下。

| 部署形态 | 配置位置 |
| --- | --- |
| 网关 | `yudao-gateway/src/main/resources/application.yaml` |
| 单体启动 | `yudao-server/src/main/resources/application.yaml` |
| 微服务 | 各 `yudao-module-*-server/src/main/resources/application.yaml` |
| 配置中心 | 上述应用各自的 Nacos 配置，需逐个应用添加 |

```yaml
# 各后端应用，例如 yudao-module-system-server
yudao:
  web:
    cors:
      allowed-origins:
        - https://admin.example.com
        - http://localhost:3000
```

本地开发写在 profile 文件里即可，例如 `yudao-server/src/main/resources/application-local.yaml` 已有 `http://localhost`。前端使用非默认端口时要把端口一起写上，例如 `http://localhost:80` 与 `http://localhost:3000` 是两个不同来源。

## 网关与后端应用的差异

| 项 | 网关 | 后端应用 |
| --- | --- | --- |
| 过滤器 | `CorsWebFilter`（Reactive） | `CorsFilter`（Servlet） |
| 执行顺序 | `HIGHEST_PRECEDENCE`，路由转发之前 | `WebFilterOrderEnum.CORS_FILTER`，最先执行 |
| `Access-Control-Allow-Credentials` | **不返回** | 恒为 `true` |
| `Access-Control-Max-Age` | `3600` | 不返回 |
| 空列表时 `Access-Control-Allow-Origin` | `*` | 回显请求的 Origin |

网关侧不开启 credentials，因此**前端经网关访问时必须用 `Authorization` 请求头传 Token，不要依赖 Cookie 会话**。

网关和后端都会处理跨域时，响应头可能出现重复的 `Access-Control-Allow-Origin`。`CorsResponseHeaderFilter` 会在响应写回前去重，只保留第一个值，这不是配置错误。

## 验证

预检请求，白名单内的 Origin 期望 `200` 且回显该 Origin：

```sh
curl -i -X OPTIONS 'http://localhost:48080/admin-api/system/auth/login' \
  -H 'Origin: https://admin.example.com' \
  -H 'Access-Control-Request-Method: POST'
```

换成白名单外的 Origin（例如 `https://evil.example.com`）应返回 `403`，且响应中不含 `Access-Control-Allow-Origin`。简单请求同样带 `Origin` 头比对：

```sh
curl -i 'http://localhost:48080/admin-api/system/tenant/get-id-by-name?name=x' \
  -H 'Origin: https://admin.example.com'
```

`allowed-origins: []` 时网关返回 `Access-Control-Allow-Origin: *`，即为兼容放行行为。单元测试：

```sh
mvn -pl yudao-gateway -am -Dtest=CorsFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl yudao-framework/yudao-spring-boot-starter-web -am -Dtest=CorsFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`-am` 和 `-Dsurefire.failIfNoSpecifiedTests=false` 都不可省：本地仓库没有 `SNAPSHOT` 同级模块，且 `-am` 带出的模块没有同名测试类。需按 `.java-version` 使用 JDK 25，否则 Lombok 在更高版本 JDK 下编译失败。

curl 和单测通过不代表已完成真实浏览器、实际前端域名和生产部署的联调，上线前需用真实管理端域名各验证一次预检与登录后接口。

## 排查

| 现象 | 原因与处理 |
| --- | --- |
| 应用启动失败，提示 `必须为完整来源列表` | 配置项含 `*`、空白或 null，改为完整 Origin |
| 浏览器报 `No 'Access-Control-Allow-Origin' header` | Origin 协议、主机或端口与白名单不一致；或该应用漏配 |
| 网关能通、直连后端 403 | 后端应用未配置相同白名单 |
| 改了配置不生效 | 未重启，或被 Nacos、profile 文件中的同名配置覆盖 |
| 响应头出现两个 `Access-Control-Allow-Origin` | 网关与后端都做了处理，由 `CorsResponseHeaderFilter` 收敛，属正常 |
| 需要跨域携带 Cookie | 网关侧不支持 credentials，改用 `Authorization` 头传 Token |
