package cn.iocoder.yudao.gateway.filter.cors;

import cn.iocoder.yudao.framework.common.pojo.CorsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/** 网关跨域校验，在路由转发之前执行。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
public class CorsFilter {

    @Bean
    @ConfigurationProperties(prefix = "yudao.web.cors")
    public CorsProperties corsProperties() {
        return new CorsProperties();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        if (properties.getAllowedOrigins().isEmpty()) {
            config.addAllowedOrigin("*");
        } else {
            config.setAllowedOrigins(properties.getAllowedOrigins());
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

}
