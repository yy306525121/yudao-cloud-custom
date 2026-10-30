# BJCA 配置加解密

独立 starter，Java 17+，Jasypt 3.0.5。不会自动接入现有业务服务。

## 接入

在需要使用的服务中添加依赖（版本由 yudao-dependencies 管理）：

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-spring-boot-starter-jasypt-bjca</artifactId>
</dependency>
```

```yaml
jasypt:
  mscp:
    url: ${BJCA_URL}
    app-id: ${BJCA_APP_ID}
    device-id: ${BJCA_DEVICE_ID}
    secret: ${BJCA_SECRET}
    main-key-id: ${BJCA_MAIN_KEY_ID}
spring:
  datasource:
    password: ENC(<平台返回的密文>)
```

支持 application 配置与已启用 Spring Cloud Bootstrap 的应用；本模块不主动开启 Bootstrap。
连接参数必须在使用密文前可读取，且自身不能使用 ENC 包裹。Bootstrap 提前消费的配置应在该阶段提供全部连接参数，不能依赖稍后加载的配置源。
配置源优先级遵循 Spring Environment；支持环境变量与占位符。Bootstrap 父容器和应用子容器各注册一次解析器，分别绑定各自环境，避免父容器无法读取 application 配置。
普通明文配置不调用 BJCA。读取密文时缺少配置、主密钥 ID 为空或远程请求失败会抛出异常。
允许用户提供名为 encryptablePropertyResolver / encryptablePropertyDetector 的相应接口 Bean 覆盖默认实现（沿用 Jasypt 命名约定）。

## 主密钥约定

生成工具输出的是原始主密钥 ID。配置项 `jasypt.mscp.main-key-id` 与 CLI 的 `mainKeyId` 参数均直接使用该原始值，无需 Base64 编码或解码。原先已编码的配置需要替换为原始主密钥 ID。

BJCA 协议保持原版：版本 1.0，ASCII key 排序后按 key=value&... 拼接，HmacSHA256 后 Base64 编码；请求明文及解密响应明文均使用 UTF-8 Base64。

## 构建和 CLI

在仓库根目录执行：

```bash
mvn -pl yudao-framework/yudao-spring-boot-starter-jasypt-bjca -am package
```

target 下普通 JAR 用于服务依赖，附加的 -cli.jar 包含工具依赖。以下用 `<cli.jar绝对路径>` 表示后者：

```bash
java -cp '<cli.jar绝对路径>' cn.iocoder.yudao.framework.jasypt.bjca.ops.cli.GenerateMainKeyCLI appId='<应用ID>' secret='<应用密钥>' deviceId='<设备ID>' url='<平台地址>' alias='<密钥别名>' description='<说明>'
java -cp '<cli.jar绝对路径>' cn.iocoder.yudao.framework.jasypt.bjca.ops.cli.EncryptCLI appId='<应用ID>' secret='<应用密钥>' deviceId='<设备ID>' url='<平台地址>' mainKeyId='<原始主密钥ID>' input='<明文1>,<明文2>'
java -cp '<cli.jar绝对路径>' cn.iocoder.yudao.framework.jasypt.bjca.ops.cli.DecryptionCLI appId='<应用ID>' secret='<应用密钥>' deviceId='<设备ID>' url='<平台地址>' mainKeyId='<原始主密钥ID>' input='<不含ENC包裹的密文>'
```

批量加密沿用逗号分隔，每条输出 ENC(...)，不回显明文。输入本身含逗号时应直接使用 BjcaClient.encrypt 方法。
保留 verbose 参数兼容性，但不打印参数内容。解密命令会输出明文；错误输出脱敏，失败退出码为 1。
Java API：BjcaClient.createKey(alias, description)、encrypt(plainText, rawKeyId)、decrypt(cipherText, rawKeyId)。

## 排查与验证边界

连接超时 10 秒，每次请求超时 30 秒；不自动重试，避免重复创建密钥。
失败时先检查配置完整性、地址可达性、应用授权、原始主密钥 ID 与密文归属。日志不会输出 secret、密文及完整平台响应。
本地测试使用模拟 HTTP 服务，不会创建真实平台密钥。真实 BJCA 联调及 Nacos 部署环境端到端验证需在公司环境另行完成。

### 本次验证结果

- 项目 BOM 实际管理 Spring Boot 3.5.8；该版本与命令行覆盖为 3.5.4 时，12 项测试均通过。
- 覆盖 application 启动绑定、真实 Spring Cloud Bootstrap 父子容器、用户覆盖、递归保护、协议签名、模拟接口及 CLI。
- 普通 JAR 与 -cli.jar 均已生成，三个打包入口的成功调用及失败退出码已实际验证。
- 验证运行于本机 JDK 25，编译目标 Java 17（class major version 61）；未在 JDK 17 运行时执行。
- 公司 settings_new.xml 镜像无法解析 Spring Boot 3.5.8，本次使用临时 Maven Central 镜像完成验证，未修改仓库配置。
