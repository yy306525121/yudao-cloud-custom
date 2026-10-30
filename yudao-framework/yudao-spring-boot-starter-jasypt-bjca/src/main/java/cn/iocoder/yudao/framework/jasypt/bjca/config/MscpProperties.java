package cn.iocoder.yudao.framework.jasypt.bjca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BJCA 连接配置。仅在读取密文时要求配置完整。
 */
@ConfigurationProperties("jasypt.mscp")
public class MscpProperties {

    /** BJCA 服务地址 */
    private String url;

    /** 原始主密钥 ID，直接使用平台返回值 */
    private String mainKeyId;

    /** 设备 ID */
    private String deviceId;

    /** 应用 ID */
    private String appId;

    /** 应用密钥，不参与日志输出 */
    private String secret;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMainKeyId() {
        return mainKeyId;
    }

    public void setMainKeyId(String mainKeyId) {
        this.mainKeyId = mainKeyId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void validateConnection() {
        if (blank(url) || blank(deviceId) || blank(appId) || blank(secret)) {
            throw new IllegalStateException("BJCA connection configuration is incomplete");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
