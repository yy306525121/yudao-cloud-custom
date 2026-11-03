package cn.iocoder.yudao.framework.common.pojo;

import java.util.ArrayList;
import java.util.List;

/** CORS 来源配置。空列表兼容放行，非空列表仅允许完整 Origin。 */
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.stream()
                .anyMatch(origin -> origin == null || origin.isBlank() || origin.contains("*"))) {
            throw new IllegalArgumentException("yudao.web.cors.allowed-origins 必须为完整来源列表，不允许空白或通配符");
        }
        this.allowedOrigins = new ArrayList<>(allowedOrigins);
    }

}
