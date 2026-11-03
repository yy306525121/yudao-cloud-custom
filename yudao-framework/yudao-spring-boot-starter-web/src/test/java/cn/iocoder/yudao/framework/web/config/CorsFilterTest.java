package cn.iocoder.yudao.framework.web.config;

import cn.iocoder.yudao.framework.common.pojo.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CorsFilterTest {
    @Test
    void checkOriginsAndPreflight() throws Exception {
        for (boolean restricted : new boolean[]{false, true}) {
            CorsProperties properties = new CorsProperties();
            if (restricted) properties.setAllowedOrigins(List.of("https://ui.example.com"));
            var filter = new YudaoWebAutoConfiguration().corsFilterBean(properties).getFilter();
            for (String origin : List.of("https://ui.example.com", "http://ui.example.com",
                    "https://ui.example.com:8443", "https://api.example.com", "")) {
                for (boolean preflight : new boolean[]{false, true}) {
                    var request = new MockHttpServletRequest(preflight ? "OPTIONS" : "POST", "/test");
                    request.setScheme("https"); request.setServerName("api.example.com"); request.setServerPort(443);
                    if (!origin.isEmpty()) request.addHeader("Origin", origin);
                    if (preflight) request.addHeader("Access-Control-Request-Method", "POST");
                    var response = new MockHttpServletResponse();
                    AtomicInteger calls = new AtomicInteger();
                    filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
                    boolean cross = !origin.isEmpty() && !origin.equals("https://api.example.com");
                    boolean denied = restricted && cross && !origin.equals("https://ui.example.com");
                    assertEquals(denied ? 403 : 200, response.getStatus());
                    assertEquals(denied || (preflight && !origin.isEmpty()) ? 0 : 1, calls.get());
                    if (cross && !denied) {
                        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
                        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
                    }
                }
            }
        }
    }
}
