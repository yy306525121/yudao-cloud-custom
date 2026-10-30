package cn.iocoder.yudao.framework.jasypt.bjca.core;

import cn.iocoder.yudao.framework.jasypt.bjca.config.MscpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/** Implements the existing BJCA wire protocol without logging request or response contents. */
public final class BjcaClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final MscpProperties properties;
    public BjcaClient(MscpProperties properties) { this.properties = properties; }

    public String createKey(String alias, String description) {
        require(alias); require(description);
        return field(call("/chiron/v1/system/createKey", Map.of("alias", alias, "description", description)), "keyId");
    }
    public String encrypt(String plainText, String keyId) {
        Objects.requireNonNull(plainText); require(keyId);
        return field(call("/cmk/v1/encrypt", Map.of("plainText", encode(plainText), "keyId", keyId)), "cipherTextBlob");
    }
    public String decrypt(String cipherText, String keyId) {
        require(cipherText); require(keyId);
        return decode(field(call("/cmk/v1/decrypt", Map.of("encData", cipherText, "keyId", keyId)), "plainText"));
    }
    private JsonNode call(String path, Map<String, String> input) {
        properties.validateConnection();
        try {
            Map<String, String> params = new TreeMap<>(input);
            params.put("version", "1.0"); params.put("signAlgo", "HmacSHA256");
            SecureRandom random = new SecureRandom();
            StringBuilder id = new StringBuilder().append(random.nextInt(9) + 1);
            for (int i = 1; i < 20; i++) id.append(random.nextInt(10));
            params.put("transId", id.toString());
            params.put("deviceId", properties.getDeviceId()); params.put("appId", properties.getAppId());
            params.put("signature", sign(params, properties.getSecret()));
            String url = properties.getUrl().replaceAll("/+$", "") + path;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(params))).build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException();
            JsonNode result = JSON.readTree(response.body());
            if (result == null || !result.path("data").isObject()) throw new IllegalStateException();
            return result.get("data");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BJCA request interrupted");
        } catch (Exception e) {
            // Do not retain parser/HTTP causes, which may contain credentials or plaintext.
            throw new IllegalStateException("BJCA request failed: " + path);
        }
    }
    public static String sign(Map<String, String> params, String secret) {
        try {
            String content = new TreeMap<>(params).entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("BJCA signing failed"); }
    }
    private static String field(JsonNode data, String name) {
        if (!data.path(name).isTextual() || data.path(name).asText().isBlank())
            throw new IllegalStateException("BJCA response missing field: " + name);
        return data.get(name).asText();
    }
    private static void require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required BJCA argument is missing");
    }
    public static String encode(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    public static String decode(String text) {
        try { return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid BJCA Base64 value"); }
    }
}
