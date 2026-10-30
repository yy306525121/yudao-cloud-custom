package cn.iocoder.yudao.framework.encrypt.core;

import cn.iocoder.yudao.framework.encrypt.config.ApiEncryptProperties;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.security.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.math.BigInteger;

/** v1 字段协议，所有加密实例均为每次调用创建，避免并发共享 Cipher。 */
public final class FieldCrypto {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Provider BC = new BouncyCastleProvider();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String algorithm;
    private final String requestKey;
    private final String responseKey;
    public FieldCrypto(ApiEncryptProperties properties) {
        algorithm = properties.getAlgorithm().toUpperCase(Locale.ROOT);
        if (!Set.of("AES", "SM4", "RSA", "SM2").contains(algorithm)) {
            throw new IllegalArgumentException("不支持的字段加密算法: " + algorithm);
        }
        requestKey = properties.getRequestKey();
        responseKey = properties.getResponseKey();
        try {
            validateKey(requestKey, false);
            validateKey(responseKey, true);
        } catch (Exception ex) { throw new IllegalArgumentException("字段加密密钥配置无效", ex); }
    }
    private void validateKey(String key, boolean publicKey) throws Exception {
        if (algorithm.equals("AES") || algorithm.equals("SM4")) {
            int n = HexFormat.of().parseHex(key).length;
            if (algorithm.equals("SM4") ? n != 16 : n != 16 && n != 24 && n != 32) {
                throw new IllegalArgumentException("密钥长度无效");
            }
        } else if (algorithm.equals("RSA")) { rsaKey(key, publicKey); }
        else { sm2Key(key, publicKey); }
    }
    public String encrypt(String value) {
        try {
            byte[] plain = JSON.writeValueAsBytes(value);
            byte[] encrypted;
            if (algorithm.equals("AES") || algorithm.equals("SM4")) {
                byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
                Cipher cipher = Cipher.getInstance(algorithm + "/GCM/NoPadding", BC);
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(HexFormat.of().parseHex(responseKey), algorithm),
                        new GCMParameterSpec(128, nonce));
                byte[] data = cipher.doFinal(plain);
                encrypted = new byte[nonce.length + data.length];
                System.arraycopy(nonce, 0, encrypted, 0, nonce.length);
                System.arraycopy(data, 0, encrypted, nonce.length, data.length);
            } else if (algorithm.equals("RSA")) {
                Key key = rsaKey(responseKey, true);
                int max = (((RSAKey) key).getModulus().bitLength() + 7) / 8 - 66;
                if (plain.length > max) throw new IllegalArgumentException("RSA 字段超过单块长度限制: " + max + " bytes");
                Cipher cipher = rsaCipher(); cipher.init(Cipher.ENCRYPT_MODE, key, oaep());
                encrypted = cipher.doFinal(plain);
            } else {
                SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
                engine.init(true, new ParametersWithRandom(sm2Key(responseKey, true), RANDOM));
                encrypted = engine.processBlock(plain, 0, plain.length);
            }
            return prefix() + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) { throw new IllegalArgumentException("字段加密失败", ex); }
    }
    public String decrypt(String value) {
        try {
            if (!value.startsWith(prefix())) throw new IllegalArgumentException("字段密文版本或算法不匹配");
            byte[] data = Base64.getDecoder().decode(value.substring(prefix().length()));
            byte[] plain;
            if (algorithm.equals("AES") || algorithm.equals("SM4")) {
                if (data.length < 28) throw new IllegalArgumentException("字段密文过短");
                Cipher cipher = Cipher.getInstance(algorithm + "/GCM/NoPadding", BC);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(HexFormat.of().parseHex(requestKey), algorithm),
                        new GCMParameterSpec(128, Arrays.copyOf(data, 12)));
                plain = cipher.doFinal(data, 12, data.length - 12);
            } else if (algorithm.equals("RSA")) {
                Key key = rsaKey(requestKey, false);
                if (data.length != ((((RSAKey) key).getModulus().bitLength() + 7) / 8)) {
                    throw new IllegalArgumentException("RSA 仅支持单块字段密文");
                }
                Cipher cipher = rsaCipher(); cipher.init(Cipher.DECRYPT_MODE, key, oaep());
                plain = cipher.doFinal(data);
            } else {
                SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
                engine.init(false, sm2Key(requestKey, false));
                plain = engine.processBlock(data, 0, data.length);
            }
            var node = JSON.readTree(plain);
            if (!node.isTextual()) throw new IllegalArgumentException("字段明文必须是 JSON 字符串");
            return node.textValue();
        } catch (Exception ex) { throw new IllegalArgumentException("字段解密失败", ex); }
    }
    private String prefix() { return "fc:v1:" + algorithm + ":"; }
    private static Cipher rsaCipher() throws Exception { return Cipher.getInstance("RSA/ECB/OAEPPadding", BC); }
    private static OAEPParameterSpec oaep() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }
    private static Key rsaKey(String value, boolean publicKey) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA", BC);
        byte[] bytes = Base64.getDecoder().decode(value);
        Key key = publicKey ? factory.generatePublic(new X509EncodedKeySpec(bytes))
                : factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        if (((RSAKey) key).getModulus().bitLength() < 2048) throw new IllegalArgumentException("RSA 至少 2048 位");
        return key;
    }
    private static org.bouncycastle.crypto.params.AsymmetricKeyParameter sm2Key(String value, boolean publicKey) {
        var curve = GMNamedCurves.getByName("sm2p256v1");
        var domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
        byte[] bytes = HexFormat.of().parseHex(value);
        if (publicKey) {
            if (bytes.length != 65 || bytes[0] != 4) throw new IllegalArgumentException("SM2 公钥格式无效");
            return new ECPublicKeyParameters(curve.getCurve().decodePoint(bytes), domain);
        }
        if (bytes.length != 32) throw new IllegalArgumentException("SM2 私钥格式无效");
        return new ECPrivateKeyParameters(new BigInteger(1, bytes), domain);
    }
}
