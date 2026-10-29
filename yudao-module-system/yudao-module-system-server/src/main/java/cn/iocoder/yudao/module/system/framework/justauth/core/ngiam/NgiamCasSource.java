package cn.iocoder.yudao.module.system.framework.justauth.core.ngiam;

import me.zhyd.oauth.config.AuthSource;
import me.zhyd.oauth.request.AuthDefaultRequest;

public enum NgiamCasSource implements AuthSource {
    NGIAM_CAS;

    // Endpoints are environment configuration, not enum constants.
    public String authorize() { return ""; }
    public String accessToken() { return ""; }
    public String userInfo() { return ""; }
    public Class<? extends AuthDefaultRequest> getTargetClass() { return NgiamCasRequest.class; }
}
