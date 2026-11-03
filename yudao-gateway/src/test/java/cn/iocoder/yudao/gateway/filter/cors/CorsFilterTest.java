package cn.iocoder.yudao.gateway.filter.cors;

import cn.iocoder.yudao.framework.common.pojo.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CorsFilterTest {
    @Test
    void checkOriginsAndPreflight() {
        for (boolean restricted : new boolean[]{false, true}) {
            CorsProperties properties = new CorsProperties();
            if (restricted) properties.setAllowedOrigins(List.of("https://ui.example.com"));
            var filter = new CorsFilter().corsWebFilter(properties);
            for (String origin : List.of("https://ui.example.com", "http://ui.example.com",
                    "https://ui.example.com:8443", "https://api.example.com", "")) {
                for (boolean preflight : new boolean[]{false, true}) {
                    var request = preflight ? MockServerHttpRequest.options("https://api.example.com/test")
                            .header("Access-Control-Request-Method", "POST")
                            : MockServerHttpRequest.post("https://api.example.com/test");
                    if (!origin.isEmpty()) request.header("Origin", origin);
                    var exchange = MockServerWebExchange.from(request);
                    AtomicInteger calls = new AtomicInteger();
                    filter.filter(exchange, e -> { calls.incrementAndGet(); return Mono.empty(); }).block();
                    boolean cross = !origin.isEmpty() && !origin.equals("https://api.example.com");
                    boolean denied = restricted && cross && !origin.equals("https://ui.example.com");
                    assertEquals(denied ? HttpStatus.FORBIDDEN : null, exchange.getResponse().getStatusCode());
                    assertEquals(denied || (preflight && !origin.isEmpty()) ? 0 : 1, calls.get());
                    if (cross && !denied && preflight) {
                        assertEquals("3600", exchange.getResponse().getHeaders().getFirst("Access-Control-Max-Age"));
                    }
                }
            }
        }
    }

    @Test
    void rejectWildcardDuringBinding() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(CorsFilter.class)
                .withPropertyValues("yudao.web.cors.allowed-origins[0]=https://*.example.com");
        runner.run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            while (failure.getCause() != null) failure = failure.getCause();
            assertTrue(failure.getMessage().contains("yudao.web.cors.allowed-origins"));
        });
    }

    @Test
    void bindWhitelistAndDefault() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(CorsFilter.class);
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBean(CorsProperties.class).getAllowedOrigins().isEmpty());
        });
        runner.withPropertyValues("yudao.web.cors.allowed-origins[0]=https://ui.example.com")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(List.of("https://ui.example.com"),
                            context.getBean(CorsProperties.class).getAllowedOrigins());
                });
    }

    @Test
    void responseHeadersDoNotRepeatChain() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
        exchange.getResponse().getHeaders().addAll("Access-Control-Allow-Origin", List.of("https://ui.example.com", "*"));
        AtomicInteger calls = new AtomicInteger();
        new CorsResponseHeaderFilter().filter(exchange, e -> {
            calls.incrementAndGet(); return Mono.empty();
        }).block();
        assertEquals(1, calls.get());
        assertEquals(List.of("https://ui.example.com"), exchange.getResponse().getHeaders().get("Access-Control-Allow-Origin"));
    }
}
