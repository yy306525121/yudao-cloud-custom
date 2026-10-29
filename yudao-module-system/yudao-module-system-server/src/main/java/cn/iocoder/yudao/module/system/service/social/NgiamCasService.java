package cn.iocoder.yudao.module.system.service.social;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.social.*;
import cn.iocoder.yudao.module.system.dal.mysql.social.*;
import cn.iocoder.yudao.module.system.framework.justauth.core.AuthRequestFactory;
import cn.iocoder.yudao.module.system.framework.justauth.core.ngiam.NgiamCasConfig;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.NgiamConfigRespVO;
import jakarta.annotation.Resource;
import me.zhyd.oauth.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum.NGIAM_CAS;

@Service
public class NgiamCasService {
    @Resource private SocialClientMapper socialClientMapper;
    @Resource private StringRedisTemplate redis;
    @Autowired(required = false) private AuthRequestFactory factory;
    @Resource private SocialUserMapper socialUserMapper;
    @Resource private SocialUserBindMapper bindMapper;

    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]); end; return v", String.class);

    public String authorize(Integer userType, String callback, String returnPath) {
        NgiamCasConfig config = loadConfiguration(userType);
        if (!config.getService().equals(callback) || !isLocalPath(returnPath)) {
            throw exception(NGIAM_STATE_INVALID);
        }
        String state = UUID.randomUUID().toString();
        String url = factory.getNgiamCasRequest(config).authorize(state);
        JSONObject context = new JSONObject().set("service", callback).set("returnPath", returnPath)
                .set("configFingerprint", config.fingerprint());
        redis.opsForValue().set(stateKey(state), context.toString(), Duration.ofMinutes(5));
        return url;
    }

    public SocialUserRespDTO login(Integer userType, String ticket, String state) {
        NgiamCasConfig config = loadConfiguration(userType);
        if (!StringUtils.hasText(ticket) || ticket.length() > 256 || state == null
                || !state.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw exception(NGIAM_STATE_INVALID);
        }
        // Atomic consume before contacting CAS: failure also requires a fresh login attempt.
        String value = redis.execute(CONSUME, Collections.singletonList(stateKey(state)));
        if (value == null) {
            throw exception(NGIAM_STATE_INVALID);
        }
        JSONObject context = JSONUtil.parseObj(value);
        if (!config.fingerprint().equals(context.getStr("configFingerprint"))
                || !config.getService().equals(context.getStr("service")) || !isLocalPath(context.getStr("returnPath"))) {
            throw exception(NGIAM_STATE_INVALID);
        }
        AuthResponse<?> response = factory.getNgiamCasRequest(config).login(AuthCallback.builder().code(ticket).state(state).build());
        if (!response.ok() || !(response.getData() instanceof AuthUser)) {
            throw exception(SOCIAL_USER_AUTH_FAILURE, "IAM 验票失败，请重新发起登录");
        }
        String username = ((AuthUser) response.getData()).getUuid();
        SocialUserDO socialUser = findExactUser(username);
        SocialUserBindDO binding = socialUser == null ? null
                : bindMapper.selectByUserTypeAndSocialUserId(userType, socialUser.getId());
        if (binding == null) {
            throw exception(NGIAM_NOT_BOUND);
        }
        // Do not persist ticket/state or consult the legacy code/state authentication cache.
        SocialUserRespDTO result = new SocialUserRespDTO(username, username, null, binding.getUserId());
        result.setRedirectPath(context.getStr("returnPath"));
        return result;
    }

    public NgiamConfigRespVO getPublicConfig() {
        NgiamCasConfig config = resolveConfiguration(UserTypeEnum.ADMIN.getValue());
        return new NgiamConfigRespVO(config != null, config == null ? null : config.getService());
    }

    private NgiamCasConfig loadConfiguration(Integer userType) {
        NgiamCasConfig config = resolveConfiguration(userType);
        if (config == null) {
            throw exception(NGIAM_CONFIG_INVALID);
        }
        return config;
    }

    private NgiamCasConfig resolveConfiguration(Integer userType) {
        if (!UserTypeEnum.ADMIN.getValue().equals(userType) || factory == null) {
            return null;
        }
        SocialClientDO client = socialClientMapper.selectBySocialTypeAndUserType(NGIAM_CAS.getType(), userType);
        if (client == null || !CommonStatusEnum.ENABLE.getStatus().equals(client.getStatus())) {
            return null;
        }
        NgiamCasConfig config = new NgiamCasConfig();
        config.setClientId(client.getId());
        config.setBaseUrl(client.getCasBaseUrl());
        config.setService(client.getCasService());
        config.setTimeoutMillis(client.getCasTimeoutMillis() == null ? 5000 : client.getCasTimeoutMillis());
        return config.isValid() ? config : null;
    }

    static boolean isLocalPath(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//") || value.contains("\\")
                || value.contains("%") || value.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return !uri.isAbsolute() && uri.getRawAuthority() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String stateKey(String state) {
        return "system:ngiam:state:" + TenantContextHolder.getRequiredTenantId() + ":" + state;
    }

    SocialUserDO findExactUser(String username) {
        // Most supported databases use case-insensitive collation. Verify the actual stored value in Java.
        return socialUserMapper.selectList(SocialUserDO::getType, NGIAM_CAS.getType(), SocialUserDO::getOpenid, username)
                .stream().filter(user -> username.equals(user.getOpenid())).findFirst().orElse(null);
    }
}
