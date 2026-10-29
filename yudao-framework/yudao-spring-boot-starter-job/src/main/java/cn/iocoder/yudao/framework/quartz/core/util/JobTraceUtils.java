package cn.iocoder.yudao.framework.quartz.core.util;

import cn.hutool.core.util.StrUtil;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Job 链路追踪工具类。
 */
public class JobTraceUtils {

    /**
     * MDC Key - 链路追踪编号
     */
    public static final String MDC_KEY_TRACE_ID = "traceId";

    private JobTraceUtils() {
    }

    public static TraceScope beginTrace(Long jobId) {
        String oldTraceId = MDC.get(MDC_KEY_TRACE_ID);
        if (StrUtil.isBlank(oldTraceId)) {
            MDC.put(MDC_KEY_TRACE_ID, buildTraceId(jobId));
        }
        return new TraceScope(oldTraceId);
    }

    private static String buildTraceId(Long jobId) {
        String prefix = jobId == null ? "job" : "job-" + jobId;
        return prefix + "-" + UUID.randomUUID();
    }

    public record TraceScope(String oldTraceId) implements AutoCloseable {

        @Override
        public void close() {
            if (oldTraceId == null) {
                MDC.remove(MDC_KEY_TRACE_ID);
            } else {
                MDC.put(MDC_KEY_TRACE_ID, oldTraceId);
            }
        }

    }

}
