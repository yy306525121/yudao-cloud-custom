package cn.iocoder.yudao.module.system.framework.justauth.core.ngiam;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import me.zhyd.oauth.model.AuthCallback;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NgiamCasRequestTest {
    private NgiamCasConfig properties() {
        NgiamCasConfig p = new NgiamCasConfig();
        p.setService("https://app.example.com/?a=1&b=2");
        p.setBaseUrl("https://iam.example.com");
        return p;
    }

    @Test
    void authorizationAndValidationUseTheSameEncodedService() {
        NgiamCasConfig p = properties();
        NgiamCasRequest adapter = new NgiamCasRequest(p);
        String url = adapter.authorize("state1");
        var query = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        assertEquals(p.getService(), URLDecoder.decode(query.getFirst("service"), StandardCharsets.UTF_8));
        assertFalse(query.containsKey("loginType"));
        assertFalse(query.containsKey("appId"));
        assertEquals("state1", query.getFirst("params"));
        assertFalse(query.containsKey("redirect_uri"));
        try (MockedStatic<HttpRequest> requests = mockStatic(HttpRequest.class)) {
            HttpRequest request = mock(HttpRequest.class, RETURNS_SELF);
            HttpResponse response = mock(HttpResponse.class);
            requests.when(() -> HttpRequest.get(anyString())).thenAnswer(invocation -> {
                var validationUri = UriComponentsBuilder.fromUriString(invocation.getArgument(0, String.class)).build();
                assertEquals("/ngiam-rst/cas/serviceValidate", validationUri.getPath());
                assertEquals("iam.example.com", validationUri.getHost());
                var params = validationUri.getQueryParams();
                assertEquals(p.getService(), URLDecoder.decode(params.getFirst("service"), StandardCharsets.UTF_8));
                assertEquals("ST-1", params.getFirst("ticket"));
                assertEquals("json", params.getFirst("format"));
                return request;
            });
            when(request.execute()).thenReturn(response);
            when(response.getStatus()).thenReturn(200);
            when(response.body()).thenReturn("{\"serviceResponse\":{\"authenticationSuccess\":{\"user\":\"Alice\"}}}");
            var result = adapter.login(AuthCallback.builder().code("ST-1").build());
            assertTrue(result.ok());
            assertEquals("Alice", result.getData().getUuid());
            assertNull(result.getData().getToken());
            verify(request).timeout(5000);
            verify(response).close();
        }
    }

    @Test
    void fixedEndpointsSupportPortAndOptionalTrailingSlash() {
        for (String base : java.util.List.of("http://10.1.102.64:80", "http://10.1.102.64:80/")) {
            NgiamCasConfig config = properties();
            config.setBaseUrl(base);
            assertTrue(config.isValid());
            assertEquals("http://10.1.102.64:80/ngiam-rst/cas/login", config.getLoginUrl());
            assertEquals("http://10.1.102.64:80/ngiam-rst/cas/serviceValidate", config.getValidateUrl());
            var url = java.net.URI.create(new NgiamCasRequest(config).authorize("state1"));
            assertEquals("/ngiam-rst/cas/login", url.getPath());
            assertEquals(80, url.getPort());
        }
    }

    @Test
    void rejectsBaseUrlsWithPathQueryFragmentCredentialsOrInvalidPort() {
        assertFalse(NgiamCasConfig.isBaseUrl(null));
        for (String base : java.util.List.of("", "iam.example.com", "ftp://iam.example.com",
                "https://iam.example.com/ngiam-rst", "https://iam.example.com//",
                "https://iam.example.com/../", "https://iam.example.com?", "https://iam.example.com?a=1",
                "https://iam.example.com#", "https://iam.example.com#x", "http://u:p@iam.example.com",
                "http://iam.example.com:0", "http://iam.example.com:70000", "http://iam.example.com:")) {
            assertFalse(NgiamCasConfig.isBaseUrl(base), base);
        }
    }

    @Test
    void rejectsFailureMissingIdentityAndMalformedResponse() {
        assertThrows(IllegalStateException.class, () -> NgiamCasRequest.verifiedUsername(
                "{\"serviceResponse\":{\"authenticationFailure\":{\"code\":\"INVALID_TICKET\"}}}"));
        assertThrows(IllegalStateException.class, () -> NgiamCasRequest.verifiedUsername(
                "{\"serviceResponse\":{\"authenticationSuccess\":{\"user\":\" \"}}}"));
        assertThrows(IllegalStateException.class, () -> NgiamCasRequest.verifiedUsername("{}"));
        assertThrows(Exception.class, () -> NgiamCasRequest.verifiedUsername("<html>error</html>"));
    }

    @Test
    void timeoutsProduceSanitizedFailureWithoutTicket() {
        try (MockedStatic<HttpRequest> requests = mockStatic(HttpRequest.class)) {
            requests.when(() -> HttpRequest.get(anyString())).thenThrow(new RuntimeException("timeout ST-secret"));
            var result = new NgiamCasRequest(properties()).login(AuthCallback.builder().code("ST-secret").build());
            assertFalse(result.ok());
            assertFalse(result.getMsg().contains("ST-secret"));
        }
    }
}
