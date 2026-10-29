package cn.iocoder.yudao.module.system.service.social;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.social.*;
import cn.iocoder.yudao.module.system.dal.mysql.social.*;
import cn.iocoder.yudao.module.system.framework.justauth.core.AuthRequestFactory;
import cn.iocoder.yudao.module.system.framework.justauth.core.ngiam.NgiamCasConfig;
import me.zhyd.oauth.model.*;
import me.zhyd.oauth.request.AuthRequest;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.*;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NgiamCasServiceTest {
    private NgiamCasService service;
    private NgiamCasConfig properties;
    private SocialClientDO client;
    private SocialClientMapper clients;
    private StringRedisTemplate redis;
    private AuthRequest request;
    private SocialUserMapper users;
    private SocialUserBindMapper bindings;
    private final String state = "12345678-1234-1234-1234-123456789abc";

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        service = new NgiamCasService();
        properties = new NgiamCasConfig();
        properties.setClientId(1L);
        properties.setBaseUrl("https://iam.example.com");
        properties.setService("https://app.example.com/");
        redis = mock(StringRedisTemplate.class);
        AuthRequestFactory factory = mock(AuthRequestFactory.class);
        request = mock(AuthRequest.class);
        when(factory.getNgiamCasRequest(any())).thenReturn(request);
        client = SocialClientDO.builder().id(1L).socialType(35).userType(2).status(0)
                .clientId("app").casBaseUrl(properties.getBaseUrl())
                .casService(properties.getService()).casTimeoutMillis(5000).build();
        clients = mock(SocialClientMapper.class);
        when(clients.selectBySocialTypeAndUserType(35, 2)).thenReturn(client);
        users = mock(SocialUserMapper.class); bindings = mock(SocialUserBindMapper.class);
        ReflectionTestUtils.setField(service, "socialClientMapper", clients);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "factory", factory);
        ReflectionTestUtils.setField(service, "socialUserMapper", users);
        ReflectionTestUtils.setField(service, "bindMapper", bindings);
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test
    void authorizationStoresTenantBoundShortLivedContext() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(request.authorize(anyString())).thenReturn("https://iam.example.com/ngiam-rst/cas/login?params=state");
        service.authorize(2, properties.getService(), "/system/menu?a=1");
        verify(values).set(startsWith("system:ngiam:state:7:"), contains("/system/menu?a=1"), eq(Duration.ofMinutes(5)));
        assertServiceException(() -> service.authorize(2, "https://evil.example.com/", "/"), NGIAM_STATE_INVALID);
        assertServiceException(() -> service.authorize(1, properties.getService(), "/"), NGIAM_CONFIG_INVALID);
    }

    private void validState() {
        when(redis.execute(any(RedisScript.class), eq(List.of("system:ngiam:state:7:" + state))))
                .thenReturn(new cn.hutool.json.JSONObject().set("service", properties.getService())
                        .set("returnPath", "/system/menu?a=1").set("configFingerprint", properties.fingerprint()).toString(), null);
    }

    private void validIdentity(String username) {
        when(request.login(any())).thenReturn(AuthResponse.<AuthUser>builder().code(2000)
                .data(AuthUser.builder().uuid(username).build()).build());
    }

    @Test
    void successConsumesStateAndReplayCannotRevalidateOrReuseDbTicket() {
        validState(); validIdentity("Alice");
        when(users.selectList(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq(35),
                any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq("Alice")))
                .thenReturn(List.of(SocialUserDO.builder().id(8L).openid("Alice").build()));
        when(bindings.selectByUserTypeAndSocialUserId(2, 8L)).thenReturn(SocialUserBindDO.builder().userId(9L).build());
        var result = service.login(2, "ST-ticket", state);
        assertEquals(9L, result.getUserId());
        assertEquals("/system/menu?a=1", result.getRedirectPath());
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_STATE_INVALID);
        verify(request, times(1)).login(any());
        verify(users, never()).selectByTypeAndCodeAnState(any(), any(), any());
        verify(users, never()).insert(any(SocialUserDO.class));
    }

    @Test
    void invalidMissingOrCrossTenantStateStopsBeforeIam() {
        assertServiceException(() -> service.login(2, "ST-ticket", null), NGIAM_STATE_INVALID);
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_STATE_INVALID);
        TenantContextHolder.setTenantId(8L);
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_STATE_INVALID);
        verifyNoInteractions(request);
    }

    @Test
    void failedValidationConsumesState() {
        validState();
        when(request.login(any())).thenReturn(AuthResponse.<AuthUser>builder().code(5000).build());
        assertServiceException(() -> service.login(2, "ST-expired", state), SOCIAL_USER_AUTH_FAILURE, "IAM 验票失败，请重新发起登录");
        assertServiceException(() -> service.login(2, "ST-expired", state), NGIAM_STATE_INVALID);
    }

    @Test
    void caseInsensitiveDatabaseMatchCannotAuthenticateDifferentAccount() {
        validState(); validIdentity("Alice");
        when(users.selectList(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq(35),
                any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq("Alice")))
                .thenReturn(List.of(SocialUserDO.builder().id(8L).openid("alice").build()));
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_NOT_BOUND);
        verifyNoInteractions(bindings);
    }

    @Test
    void changedCallbackConfigurationInvalidatesOutstandingLogin() {
        validState();
        client.setCasService("https://app.example.com/new-callback");
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_STATE_INVALID);
        verifyNoInteractions(request);
    }

    @Test
    void disabledProviderCannotAuthorizeOrValidate() {
        client.setStatus(1);
        assertServiceException(() -> service.authorize(2, properties.getService(), "/"), NGIAM_CONFIG_INVALID);
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_CONFIG_INVALID);
        verifyNoInteractions(request, redis);
    }

    @Test
    void publicConfigExposesOnlyAvailabilityAndService() {
        assertTrue(service.getPublicConfig().isEnabled());
        assertEquals(properties.getService(), service.getPublicConfig().getService());
        client.setStatus(1);
        assertFalse(service.getPublicConfig().isEnabled());
        assertNull(service.getPublicConfig().getService());
        when(clients.selectBySocialTypeAndUserType(35, 2)).thenReturn(null);
        assertFalse(service.getPublicConfig().isEnabled());
    }

    @Test
    void changingBaseUrlInvalidatesOutstandingState() {
        validState();
        client.setCasBaseUrl("https://new-iam.example.com");
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_STATE_INVALID);
        verifyNoInteractions(request);
    }

    @Test
    void invalidDatabaseConfigurationDisablesProvider() {
        client.setCasBaseUrl("javascript:alert(1)");
        assertFalse(service.getPublicConfig().isEnabled());
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_CONFIG_INVALID);
    }

    @Test
    void unconfiguredLegacyApplicationStaysUnavailableUntilManuallySaved() {
        client.setCasBaseUrl(null);
        assertFalse(service.getPublicConfig().isEnabled());
        assertServiceException(() -> service.authorize(2, properties.getService(), "/"), NGIAM_CONFIG_INVALID);
        assertServiceException(() -> service.login(2, "ST-ticket", state), NGIAM_CONFIG_INVALID);
        verifyNoInteractions(request, redis);
        client.setCasBaseUrl("https://iam.example.com/");
        assertTrue(service.getPublicConfig().isEnabled());
    }

    @Test
    void rejectsExternalAndAmbiguousReturnPaths() {
        for (String path : List.of("https://evil.example.com", "//evil.example.com", "/\\evil", "/%2f%2fevil", "/\n")) {
            assertFalse(NgiamCasService.isLocalPath(path));
        }
        assertTrue(NgiamCasService.isLocalPath("/system/menu?a=1"));
    }
}
