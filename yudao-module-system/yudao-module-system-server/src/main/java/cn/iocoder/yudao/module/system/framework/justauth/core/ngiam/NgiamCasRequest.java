package cn.iocoder.yudao.module.system.framework.justauth.core.ngiam;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.enums.AuthResponseStatus;
import me.zhyd.oauth.model.*;
import me.zhyd.oauth.request.AuthDefaultRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/** CAS protocol adapter. One-time state validation is performed by NgiamCasService before login. */
public class NgiamCasRequest extends AuthDefaultRequest {
    private final NgiamCasConfig properties;

    public NgiamCasRequest(NgiamCasConfig properties) {
        // JustAuth requires non-empty OAuth fields internally; neither placeholder is sent to CAS.
        super(AuthConfig.builder().clientId("CAS_NO_CLIENT_ID").clientSecret("CAS_NO_CLIENT_SECRET")
                .redirectUri(properties.getService()).ignoreCheckState(true).build(), NgiamCasSource.NGIAM_CAS);
        this.properties = properties;
    }

    @Override
    public String authorize(String state) {
        return UriComponentsBuilder.fromUriString(properties.getLoginUrl())
                .replaceQueryParam("service", "{service}")
                .replaceQueryParam("loginType")
                .replaceQueryParam("appId")
                .replaceQueryParam("params", "{state}").encode()
                .buildAndExpand(properties.getService(), state).toUriString();
    }

    @Override
    public AuthToken getAccessToken(AuthCallback callback) {
        // CAS does not issue an OAuth access token; do not retain the ticket in a token or DB record.
        String url = UriComponentsBuilder.fromUriString(properties.getValidateUrl())
                .replaceQueryParam("ticket", "{ticket}").replaceQueryParam("service", "{service}")
                .replaceQueryParam("format", "json").encode()
                .buildAndExpand(callback.getCode(), properties.getService()).toUriString();
        try (HttpResponse response = HttpRequest.get(url).timeout(properties.getTimeoutMillis())
                .disableCookie().execute()) {
            if (response.getStatus() != 200) {
                throw new IllegalStateException("IAM 验票服务暂不可用，请重新发起登录");
            }
            return AuthToken.builder().accessToken(verifiedUsername(response.body())).build();
        }
    }

    static String verifiedUsername(String body) {
        JSONObject envelope = JSONUtil.parseObj(body).getJSONObject("serviceResponse");
        JSONObject success = envelope == null ? null : envelope.getJSONObject("authenticationSuccess");
        if (envelope == null || envelope.containsKey("authenticationFailure") || success == null
                || !StringUtils.hasText(success.getStr("user"))) {
            throw new IllegalStateException("IAM 票据无效、已过期或用户不可用，请重新发起登录");
        }
        return success.getStr("user");
    }

    @Override
    public AuthUser getUserInfo(AuthToken token) {
        return AuthUser.builder().uuid(token.getAccessToken()).username(token.getAccessToken())
                .nickname(token.getAccessToken()).source("NGIAM_CAS").build();
    }

    @Override
    public AuthResponse<AuthUser> login(AuthCallback callback) {
        // Avoid JustAuth's default exception logging, which can include credential-bearing request URLs.
        try {
            if (!StringUtils.hasText(callback.getCode())) {
                throw new IllegalArgumentException("ticket 不能为空");
            }
            return AuthResponse.<AuthUser>builder().code(AuthResponseStatus.SUCCESS.getCode()).data(getUserInfo(getAccessToken(callback))).build();
        } catch (Exception ex) {
            return AuthResponse.<AuthUser>builder().code(AuthResponseStatus.FAILURE.getCode())
                    .msg("IAM 验票失败，请重新发起登录或联系管理员").build();
        }
    }
}
