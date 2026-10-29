package cn.iocoder.yudao.framework.tenant.core.job;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import com.xxl.job.core.context.XxlJobContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class TenantJobAspectTest {
    @Test
    void jobHandlerWithoutXxlContextDoesNotSwallowFailure() throws Throwable {
        TenantFrameworkService tenants = mock(TenantFrameworkService.class);
        when(tenants.getTenantIds()).thenReturn(List.of(1L));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenThrow(new IllegalStateException("failed"));
        assertThatThrownBy(() -> new TenantJobAspect(tenants).around(point, null)).hasMessageContaining("failed");
    }

    @Test
    void propagatesTraceAndAggregatesFailureWithoutLeakingCallerContext() throws Throwable {
        TenantFrameworkService tenants = mock(TenantFrameworkService.class);
        when(tenants.getTenantIds()).thenReturn(List.of(1L, 2L));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        var visited = ConcurrentHashMap.<Long>newKeySet();
        XxlJobContext context = new XxlJobContext(1L, "param", "", 0, 1);
        when(point.proceed()).thenAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo("job-trace");
            assertThat(XxlJobContext.getXxlJobContext().getJobParam()).isEqualTo("param");
            Long tenantId = TenantContextHolder.getTenantId();
            visited.add(tenantId);
            if (tenantId == 2L) throw new IllegalStateException("tenant failed");
            return "ok";
        });
        MDC.put("traceId", "job-trace");
        XxlJobContext.setXxlJobContext(context);
        try {
            new TenantJobAspect(tenants).around(point, null);
            assertThat(visited).containsExactlyInAnyOrder(1L, 2L);
            assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
            assertThat(context.getHandleMsg()).contains("tenant failed", "ok");
            assertThat(MDC.get("traceId")).isEqualTo("job-trace");
            assertThat(TenantContextHolder.getTenantId()).isNull();
        } finally {
            MDC.clear();
            XxlJobContext.setXxlJobContext(null);
        }
    }
}
