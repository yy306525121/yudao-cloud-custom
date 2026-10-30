package cn.iocoder.yudao.framework.encrypt;

import cn.iocoder.yudao.framework.encrypt.config.ApiEncryptProperties;
import cn.iocoder.yudao.framework.encrypt.core.FieldCrypto;
import cn.iocoder.yudao.framework.encrypt.core.FieldPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiEncryptTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KEY = "0123456789abcdeffedcba9876543210";
    static ApiEncryptProperties properties(String algorithm) throws Exception {
        var p = new ApiEncryptProperties(); p.setEnable(true); p.setAlgorithm(algorithm);
        if (algorithm.equals("AES") || algorithm.equals("SM4")) {
            p.setRequestKey(KEY); p.setResponseKey(KEY);
        } else if (algorithm.equals("RSA")) {
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            var pair = generator.generateKeyPair();
            p.setRequestKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            p.setResponseKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        } else {
            var generator = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
            generator.initialize(new ECNamedCurveGenParameterSpec("sm2p256v1"));
            var pair = generator.generateKeyPair();
            p.setRequestKey(String.format("%064x", ((ECPrivateKey) pair.getPrivate()).getD()));
            p.setResponseKey(HexFormat.of().formatHex(((ECPublicKey) pair.getPublic()).getQ().getEncoded(false)));
        }
        return p;
    }
    @Test void algorithmsAndAuthentication() throws Exception {
        for (String algorithm : List.of("AES", "SM4", "RSA", "SM2")) {
            var crypto = new FieldCrypto(properties(algorithm));
            for (String plain : List.of("", "手机号13800138000", "引号\"与换行\n😀")) {
                String encrypted = crypto.encrypt(plain);
                assertEquals(plain, crypto.decrypt(encrypted));
                assertNotEquals(encrypted, crypto.encrypt(plain));
                byte[] data = Base64.getDecoder().decode(encrypted.substring(encrypted.lastIndexOf(':') + 1));
                data[data.length - 1] ^= 1;
                String tampered = "fc:v1:" + algorithm + ":" + Base64.getEncoder().encodeToString(data);
                assertThrows(IllegalArgumentException.class, () -> crypto.decrypt(tampered));
            }
            assertThrows(IllegalArgumentException.class, () -> crypto.decrypt("plaintext"));
            assertThrows(IllegalArgumentException.class, () -> crypto.decrypt("fc:v2:" + algorithm + ":aaaa"));
        }
        var sm4 = new FieldCrypto(properties("SM4"));
        String longText = "长字段".repeat(10000);
        assertEquals(longText, sm4.decrypt(sm4.encrypt(longText)));
        var rsa = new FieldCrypto(properties("RSA"));
        assertThrows(IllegalArgumentException.class, () -> rsa.encrypt("长".repeat(100)));
    }
    @Test void paths() throws Exception {
        byte[] source = "{\"code\":0,\"data\":{\"mobile\":\"\",\"list\":[{\"mobile\":\"138\"},{\"mobile\":null},{}],\"tags\":[\"a\",null]}}".getBytes(StandardCharsets.UTF_8);
        String[] paths = {"data.mobile", "data.list[*].mobile", "data.tags[*]", "data.mobile", "missing"};
        var crypto = new FieldCrypto(properties("SM4"));
        byte[] encrypted = FieldPaths.transform(source, paths, crypto::encrypt);
        assertEquals(0, JSON.readTree(encrypted).get("code").intValue());
        assertEquals(JSON.readTree(source), JSON.readTree(FieldPaths.transform(encrypted, paths, crypto::decrypt)));
        for (String path : List.of("$", "data..mobile", "data[0]", "__proto__.x", "data.list", "data.list.mobile", "data.mobile[*]")) {
            assertThrows(IllegalArgumentException.class, () -> FieldPaths.transform(source, new String[]{path}, crypto::encrypt), path);
        }
        assertThrows(IllegalArgumentException.class, () -> FieldPaths.transform("whole-cipher".getBytes(), paths, crypto::decrypt));
    }
    @Test void sharedInteropFixtures() throws Exception {
        try (var input = getClass().getResourceAsStream("/field-crypto-v1.json")) {
            assertNotNull(input);
            for (var vector : JSON.readTree(input)) {
                var p = new ApiEncryptProperties(); p.setAlgorithm(vector.get("algorithm").asText());
                p.setRequestKey(vector.get("serverRequestKey").asText());
                p.setResponseKey(vector.get("serverResponseKey").asText());
                var crypto = new FieldCrypto(p);
                for (var item : vector.get("cases")) {
                    assertEquals(item.get("plain").asText(), crypto.decrypt(item.get("cipher").asText()));
                }
            }
        }
    }
    // Standalone interop runner: emits test-only keys or verifies JavaScript ciphertext.
    public static void main(String[] args) throws Exception {
        var suite = new ApiEncryptTest(); suite.algorithmsAndAuthentication(); suite.paths();
        if (args[0].equals("generate")) {
            var vectors = new ArrayList<Map<String, Object>>();
            for (String algorithm : List.of("AES", "SM4", "RSA", "SM2")) {
                var properties = properties(algorithm); var crypto = new FieldCrypto(properties);
                var vector = new LinkedHashMap<String, Object>();
                vector.put("algorithm", algorithm); vector.put("serverRequestKey", properties.getRequestKey());
                vector.put("serverResponseKey", properties.getResponseKey());
                vector.put("requestKey", algorithm.equals("RSA") || algorithm.equals("SM2") ? properties.getResponseKey() : KEY);
                vector.put("responseKey", algorithm.equals("RSA") || algorithm.equals("SM2") ? properties.getRequestKey() : KEY);
                var cases = new ArrayList<Map<String, String>>();
                for (String plain : List.of("", "手机号13800138000", "引号\"与换行\n😀")) cases.add(Map.of("plain", plain, "cipher", crypto.encrypt(plain)));
                vector.put("cases", cases); vectors.add(vector);
            }
            JSON.writeValue(Path.of(args[1]).toFile(), vectors);
        } else {
            for (var vector : JSON.readTree(Files.readAllBytes(Path.of(args[1])))) {
                var p = new ApiEncryptProperties(); p.setAlgorithm(vector.get("algorithm").asText());
                p.setRequestKey(vector.get("serverRequestKey").asText()); p.setResponseKey(vector.get("serverResponseKey").asText());
                var crypto = new FieldCrypto(p);
                for (var item : vector.get("cases")) assertEquals(item.get("plain").asText(), crypto.decrypt(item.get("jsCipher").asText()));
            }
        }
        System.out.println("Java algorithm, authentication, paths and interop checks passed");
    }
}
