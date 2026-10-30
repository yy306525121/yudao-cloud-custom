package cn.iocoder.yudao.framework.jasypt.bjca.ops.cli;

import cn.iocoder.yudao.framework.jasypt.bjca.config.*;
import cn.iocoder.yudao.framework.jasypt.bjca.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ulisesbocchio.jasyptspringboot.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import java.net.InetSocketAddress;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class BjcaTest {
    HttpServer server;
    String url;
    AtomicInteger calls = new AtomicInteger();
    volatile int status = 200;
    volatile String response;
    final ObjectMapper json = new ObjectMapper();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Map<String, String> params = json.readValue(exchange.getRequestBody(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                String signature = params.remove("signature");
                assertThat(signature).isEqualTo(BjcaClient.sign(params, "test-secret"));
                assertThat(params).containsEntry("version", "1.0").containsEntry("signAlgo", "HmacSHA256");
                assertThat(params.get("transId")).matches("[1-9][0-9]{19}");
                String path = exchange.getRequestURI().getPath();
                if (!path.endsWith("createKey")) assertThat(params.get("keyId")).isEqualTo("key-1");
                String body = response;
                if (body == null) {
                    if (path.endsWith("createKey")) body = "{\"data\":{\"keyId\":\"key-1\"}}";
                    else if (path.endsWith("/encrypt")) {
                        assertThat(BjcaClient.decode(params.get("plainText"))).isIn("中文密码", "one", "two");
                        body = "{\"data\":{\"cipherTextBlob\":\"cipher\"}}";
                    } else body = "{\"data\":{\"plainText\":\"" + BjcaClient.encode("中文密码") + "\"}}";
                }
                calls.incrementAndGet();
                byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start(); url = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }
    MscpProperties properties() {
        MscpProperties p = new MscpProperties(); p.setUrl(url); p.setAppId("app");
        p.setDeviceId("device"); p.setSecret("test-secret"); p.setMainKeyId("key-1"); return p;
    }
    ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(BjcaAutoConfiguration.class,
                com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration.class))
                .withPropertyValues("jasypt.mscp.url=" + url, "jasypt.mscp.app-id=app", "jasypt.mscp.device-id=device",
                        "jasypt.mscp.secret=test-secret", "jasypt.mscp.main-key-id=" + "key-1");
    }
    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(SampleProperties.class)
    static class Application { }

    @org.springframework.boot.context.properties.ConfigurationProperties("sample")
    public static class SampleProperties {
        private String password;
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @Test void realApplicationConfiguration(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.writeString(directory.resolve("application.properties"),
                "jasypt.mscp.url=" + url + "\njasypt.mscp.app-id=app\njasypt.mscp.device-id=device\n"
                + "jasypt.mscp.secret=test-secret\njasypt.mscp.main-key-id=" + "key-1"
                + "\nsample.password=ENC(cipher)\n");
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(Application.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .run("--spring.config.location=" + directory.toUri(), "--spring.main.banner-mode=off")) {
            assertThat(context.getEnvironment().getProperty("sample.password")).isEqualTo("中文密码");
            assertThat(context.getBean(SampleProperties.class).getPassword()).isEqualTo("中文密码");
            assertThat(context.getBean(MscpProperties.class).getAppId()).isEqualTo("app");
        }
    }

    @Test void realCloudBootstrap(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path bootstrap = directory.resolve("bootstrap.properties");
        java.nio.file.Files.writeString(bootstrap,
                "jasypt.mscp.url=" + url + "\njasypt.mscp.app-id=app\njasypt.mscp.device-id=device\n"
                + "jasypt.mscp.secret=test-secret\njasypt.mscp.main-key-id=" + "key-1" + "\n");
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(Application.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .run("--spring.cloud.bootstrap.location=" + bootstrap.toUri(),
                        "--sample.password=ENC(cipher)", "--spring.main.banner-mode=off")) {
            assertThat(context.getParent()).isNotNull();
            assertThat(context.getEnvironment().getProperty("sample.password")).isEqualTo("中文密码");
            assertThat(context.getBean(SampleProperties.class).getPassword()).isEqualTo("中文密码");
            assertThat(context.getBean("encryptablePropertyResolver"))
                    .isNotSameAs(context.getParent().getBean("encryptablePropertyResolver"));
        }
    }

    @Test void bootstrapRegistration() throws Exception {
        // Verify the actual legacy resource key without adding a Cloud runtime to this starter.
        var resources = getClass().getClassLoader().getResources("META-INF/spring.factories");
        boolean found = false;
        while (resources.hasMoreElements()) {
            Properties properties = new Properties();
            try (var input = resources.nextElement().openStream()) { properties.load(input); }
            found |= BjcaAutoConfiguration.class.getName().equals(properties.getProperty("org.springframework.cloud.bootstrap.BootstrapConfiguration"));
        }
        assertThat(found).isTrue();
    }

    @Test void signatureKnownVector() {
        assertThat(BjcaClient.sign(Map.of("z", "2", "a", "1"), "test-secret"))
                .isEqualTo("nzWGgj9j9JgatHA6DBEnHkl8P8xWXyJksMD0c7Bu8pk=");
    }

    @Test void encryptedConnectionSettingsFailWithoutRecursion() {
        runner().withPropertyValues("jasypt.mscp.secret=ENC(secret)").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(calls.get()).isZero();
        });
    }

    @Test void protocol() {
        BjcaClient client = new BjcaClient(properties());
        assertThat(client.createKey("alias", "description")).isEqualTo("key-1");
        assertThat(client.encrypt("中文密码", "key-1")).isEqualTo("cipher");
        assertThat(client.decrypt("cipher", "key-1")).isEqualTo("中文密码");
        assertThat(calls.get()).isEqualTo(3);
    }
    @Test void wrappedPropertiesAndBinding() {
        runner().withPropertyValues("sample.password= ENC(cipher) ", "sample.plain=normal").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(MscpProperties.class);
            assertThat(ctx.getEnvironment().getProperty("sample.password")).isEqualTo("中文密码");
            assertThat(ctx.getEnvironment().getProperty("sample.plain")).isEqualTo("normal");
        });
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void missingSettingsAndMissingKey() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(BjcaAutoConfiguration.class))
                .run(ctx -> {
                    EncryptablePropertyResolver resolver = ctx.getBean("encryptablePropertyResolver", EncryptablePropertyResolver.class);
                    assertThat(resolver.resolvePropertyValue(null)).isNull();
                    assertThat(resolver.resolvePropertyValue("plain")).isEqualTo("plain");
                    assertThatThrownBy(() -> resolver.resolvePropertyValue("ENC(cipher)")).hasMessageContaining("decryption failed");
                });
        runner().withPropertyValues("jasypt.mscp.main-key-id=")
                .run(ctx -> assertThatThrownBy(() -> ctx.getBean("encryptablePropertyResolver", EncryptablePropertyResolver.class).resolvePropertyValue("ENC(cipher)"))
                        .hasMessageContaining("decryption failed"));
        assertThat(calls.get()).isZero();
    }
    @Test void failuresAreSanitized() {
        BjcaClient client = new BjcaClient(properties());
        status = 500; response = "sensitive-server-response";
        assertThatThrownBy(() -> client.decrypt("cipher", "key-1")).hasMessageNotContaining(response).hasNoCause();
        status = 200;
        for (String body : List.of("not-json", "{}", "{\"data\":{}}", "{\"data\":{\"plainText\":\"%%%\"}}")) {
            response = body;
            assertThatThrownBy(() -> client.decrypt("cipher", "key-1")).hasNoCause();
        }
    }
    @Configuration(proxyBeanMethods = false)
    static class Custom {
        @Bean("encryptablePropertyResolver") EncryptablePropertyResolver customResolver() { return value -> "custom"; }
    }
    @Test void customResolverWins() {
        runner().withUserConfiguration(Custom.class).run(ctx -> {
            assertThat(ctx).hasBean("encryptablePropertyResolver");
            assertThat(ctx.getBean("encryptablePropertyResolver", EncryptablePropertyResolver.class).resolvePropertyValue("ENC(cipher)")).isEqualTo("custom");
        });
    }
    @Test void bootstrapParentUsesContextLocalResolver() {
        runner().run(parent -> runner().withParent(parent).run(child -> {
            assertThat(child).hasNotFailed();
            assertThat(child.getBean("encryptablePropertyResolver", EncryptablePropertyResolver.class)).isNotSameAs(parent.getBean("encryptablePropertyResolver", EncryptablePropertyResolver.class));
        }));
    }
    @Test void cliOperationsAndErrors() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        List<String> common = List.of("url=" + url, "appId=app", "deviceId=device", "secret=test-secret");
        for (String operation : List.of("create", "encrypt", "decrypt")) {
            List<String> args = new ArrayList<>(common);
            args.addAll(operation.equals("create") ? List.of("alias=alias", "description=description")
                    : List.of("mainKeyId=key-1", "input=" + (operation.equals("encrypt") ? "one,two" : "cipher")));
            assertThat(CliSupport.run(operation, args.toArray(String[]::new), new PrintStream(out), new PrintStream(err))).isZero();
        }
        assertThat(out.toString()).contains("key-1", "ENC(cipher)", "中文密码").doesNotContain("test-secret", "one", "two");
        assertThat(CliSupport.run("encrypt", new String[0], new PrintStream(out), new PrintStream(err))).isEqualTo(1);
        assertThat(CliSupport.run("encrypt", new String[]{"secret=test-secret", "bad=private"}, new PrintStream(out), new PrintStream(err))).isEqualTo(1);
        assertThat(err.toString()).doesNotContain("test-secret", "private");
    }
}
