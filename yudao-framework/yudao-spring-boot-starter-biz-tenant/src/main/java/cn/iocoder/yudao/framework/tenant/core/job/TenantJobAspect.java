package cn.iocoder.yudao.framework.tenant.core.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多租户 JobHandler AOP
 * 任务执行时，会按照租户逐个执行 Job 的逻辑
 *
 * 注意，需要保证 JobHandler 的幂等性。因为 Job 因为某个租户执行失败重试时，之前执行成功的租户也会再次执行。
 *
 * @author 芋道源码
 */
@Aspect
@RequiredArgsConstructor
@Slf4j
public class TenantJobAspect {

    private final TenantFrameworkService tenantFrameworkService;

    @Around("@annotation(tenantJob)")
    public Object around(ProceedingJoinPoint joinPoint, TenantJob tenantJob) {
        // 获得租户列表
        List<Long> tenantIds = tenantFrameworkService.getTenantIds();
        if (CollUtil.isEmpty(tenantIds)) {
            return "{}";
        }

        // 逐个租户，执行 Job
        Map<Long, String> results = new ConcurrentHashMap<>();
        AtomicBoolean success = new AtomicBoolean(true); // 标记，是否存在失败的情况
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext(); // XXL-Job 上下文
        Map<String, String> traceContext = MDC.getCopyOfContextMap();
        tenantIds.parallelStream().forEach(tenantId -> {
            // TODO 芋艿：先通过 parallel 实现并行；1）多个租户，是一条执行日志；2）异常的情况
            TenantUtils.execute(tenantId, () -> {
                Map<String, String> oldMdc = MDC.getCopyOfContextMap();
                XxlJobContext oldContext = XxlJobContext.getXxlJobContext();
                XxlJobContext tenantContext = xxlJobContext == null ? null : new XxlJobContext(
                        xxlJobContext.getJobId(), xxlJobContext.getJobParam(), xxlJobContext.getJobLogFileName(),
                        xxlJobContext.getShardIndex(), xxlJobContext.getShardTotal());
                try {
                    if (traceContext == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(traceContext);
                    }
                    XxlJobContext.setXxlJobContext(tenantContext);
                    // 执行 Job
                    Object result = joinPoint.proceed();
                    if (tenantContext != null && tenantContext.getHandleCode() != XxlJobContext.HANDLE_CODE_SUCCESS) {
                        success.set(false);
                        results.put(tenantId, StrUtil.toStringOrEmpty(tenantContext.getHandleMsg()));
                    } else {
                        results.put(tenantId, StrUtil.toStringOrEmpty(result != null ? result
                                : tenantContext == null ? null : tenantContext.getHandleMsg()));
                    }
                } catch (Throwable e) {
                    results.put(tenantId, ExceptionUtil.getRootCauseMessage(e));
                    success.set(false);
                    // 打印异常
                    XxlJobHelper.log(StrUtil.format("[多租户({}) 执行任务({})，发生异常：{}]",
                            tenantId, joinPoint.getSignature(), ExceptionUtils.getStackTrace(e)));
                } finally {
                    XxlJobContext.setXxlJobContext(oldContext);
                    if (oldMdc == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(oldMdc);
                    }
                }
            });
        });
        // 记录执行结果
        String result = JsonUtils.toJsonString(results);
        if (xxlJobContext != null) {
            if (success.get()) {
                XxlJobHelper.handleSuccess(result);
            } else {
                XxlJobHelper.handleFail(result);
            }
        } else if (!success.get()) {
            throw new IllegalStateException(result);
        }
        return result;
    }

}
