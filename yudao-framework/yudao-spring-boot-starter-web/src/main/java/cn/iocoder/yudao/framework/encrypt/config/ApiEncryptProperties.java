package cn.iocoder.yudao.framework.encrypt.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 字段级 API 加解密配置；不再支持整包加解密。 */
@ConfigurationProperties(prefix = "yudao.api-encrypt")
@Validated
@Data
public class ApiEncryptProperties {
    @NotNull
    private Boolean enable;
    /** AES、SM4、RSA 或 SM2。 */
    @NotEmpty
    private String algorithm = "SM4";
    /** 对称算法为 HEX；RSA 为 PKCS8 DER Base64 私钥；SM2 为 32 字节 HEX 私钥。 */
    @NotEmpty
    private String requestKey;
    /** 对称算法为 HEX；RSA 为 SPKI DER Base64 公钥；SM2 为 04 开头的非压缩 HEX 公钥。 */
    @NotEmpty
    private String responseKey;
}
