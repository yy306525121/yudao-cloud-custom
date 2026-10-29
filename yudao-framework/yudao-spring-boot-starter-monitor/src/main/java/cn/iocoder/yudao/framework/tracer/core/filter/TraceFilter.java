package cn.iocoder.yudao.framework.tracer.core.filter;

import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Trace 过滤器，打印 traceId 到 header 中返回
 *
 * @author 芋道源码
 */
public class TraceFilter extends OncePerRequestFilter {

    /**
     * Header 名 - 链路追踪编号
     */
    public static final String MDC_KEY_TRACE_ID = "traceId";

    public static final String HEADER_NAME_TRACE_ID = "trace-id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String oldTraceId = MDC.get(MDC_KEY_TRACE_ID);
        String traceId = oldTraceId;
        if (traceId == null || traceId.isBlank()) {
            traceId = TracerUtils.getTraceId();
            if (traceId == null || traceId.isBlank() || "N/A".equals(traceId)) {
                traceId = UUID.randomUUID().toString();
            }
        }
        MDC.put(MDC_KEY_TRACE_ID, traceId);
        response.addHeader(HEADER_NAME_TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (oldTraceId == null) {
                MDC.remove(MDC_KEY_TRACE_ID);
            } else {
                MDC.put(MDC_KEY_TRACE_ID, oldTraceId);
            }
        }
    }

}
