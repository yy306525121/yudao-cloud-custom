package cn.iocoder.yudao.module.system.framework.justauth.core.ngiam;

import lombok.Data;

@Data
public class NgiamCasConfig {
    private Long clientId;
    private String baseUrl;
    private String service;
    private int timeoutMillis = 5000;

    public boolean isValid() {
        return isBaseUrl(baseUrl) && isHttpUrl(service)
                && timeoutMillis >= 100 && timeoutMillis <= 60000;
    }

    public String getLoginUrl() {
        return endpoint("/ngiam-rst/cas/login");
    }

    public String getValidateUrl() {
        return endpoint("/ngiam-rst/cas/serviceValidate");
    }

    private String endpoint(String path) {
        if (!isBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("IAM 对接地址必须是 http/https 协议、主机及可选端口");
        }
        java.net.URI uri = java.net.URI.create(baseUrl);
        return uri.getScheme() + "://" + uri.getRawAuthority() + path;
    }

    public static boolean isBaseUrl(String value) {
        if (value == null || value.length() > 2048 || !isHttpUrl(value)) {
            return false;
        }
        java.net.URI uri = java.net.URI.create(value);
        String path = uri.getRawPath();
        return (path == null || path.isEmpty() || "/".equals(path))
                && uri.getRawQuery() == null && !uri.getRawAuthority().endsWith(":")
                && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535));
    }

    public static boolean isHttpUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getFragment() == null && uri.getUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String fingerprint() {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(this));
    }
}
