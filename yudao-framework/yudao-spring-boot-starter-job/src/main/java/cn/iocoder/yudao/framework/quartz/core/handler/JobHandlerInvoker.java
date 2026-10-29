package cn.iocoder.yudao.framework.quartz.core.handler;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.iocoder.yudao.framework.quartz.core.enums.JobDataKeyEnum;
import cn.iocoder.yudao.framework.quartz.core.service.JobLogFrameworkService;
import cn.iocoder.yudao.framework.quartz.core.util.JobTraceUtils;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.scheduling.quartz.QuartzJobBean;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static cn.hutool.core.exceptions.ExceptionUtil.getRootCauseMessage;

/**
 * 基础 Job 调用者，负责调用 {@link JobHandler#execute(String)} 或兼容调用 {@code @XxlJob} 方法。
 */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
@Slf4j
public class JobHandlerInvoker extends QuartzJobBean {

    @Resource
    private JobHandlerRegistry jobHandlerRegistry;

    @Resource
    private JobLogFrameworkService jobLogFrameworkService;

    @Override
    protected void executeInternal(JobExecutionContext executionContext) throws JobExecutionException {
        Long jobId = executionContext.getMergedJobDataMap().getLong(JobDataKeyEnum.JOB_ID.name());
        String jobHandlerName = executionContext.getMergedJobDataMap().getString(JobDataKeyEnum.JOB_HANDLER_NAME.name());
        String jobHandlerParam = executionContext.getMergedJobDataMap().getString(JobDataKeyEnum.JOB_HANDLER_PARAM.name());
        int refireCount = executionContext.getRefireCount();
        int retryCount = (Integer) executionContext.getMergedJobDataMap().getOrDefault(JobDataKeyEnum.JOB_RETRY_COUNT.name(), 0);
        int retryInterval = (Integer) executionContext.getMergedJobDataMap().getOrDefault(JobDataKeyEnum.JOB_RETRY_INTERVAL.name(), 0);

        Long jobLogId = null;
        LocalDateTime startTime = LocalDateTime.now();
        String data = null;
        Throwable exception = null;
        try (JobTraceUtils.TraceScope ignored = JobTraceUtils.beginTrace(jobId)) {
            try {
                jobLogId = jobLogFrameworkService.createJobLog(jobId, startTime, jobHandlerName, jobHandlerParam, refireCount + 1);
                data = jobHandlerRegistry.execute(jobId, jobHandlerName, jobHandlerParam);
            } catch (Throwable ex) {
                exception = ex;
            }

            updateJobLogResultAsync(jobLogId, startTime, data, exception, executionContext);
            handleException(exception, refireCount, retryCount, retryInterval);
        }
    }

    private void updateJobLogResultAsync(Long jobLogId, LocalDateTime startTime, String data, Throwable exception,
                                         JobExecutionContext executionContext) {
        if (jobLogId == null) {
            return;
        }
        LocalDateTime endTime = LocalDateTime.now();
        boolean success = exception == null;
        if (!success) {
            data = getRootCauseMessage(exception);
        }
        try {
            jobLogFrameworkService.updateJobLogResultAsync(jobLogId, endTime,
                    (int) LocalDateTimeUtil.between(startTime, endTime).toMillis(), success, data);
        } catch (Exception ex) {
            log.error("[executeInternal][Job({}) logId({}) 记录执行日志失败({}/{})]",
                    executionContext.getJobDetail().getKey(), jobLogId, success, data, ex);
        }
    }

    private void handleException(Throwable exception,
                                 int refireCount, int retryCount, int retryInterval) throws JobExecutionException {
        if (exception == null) {
            return;
        }
        if (refireCount >= retryCount) {
            throw new JobExecutionException(exception);
        }

        if (retryInterval > 0) {
            ThreadUtil.sleep(retryInterval);
        }
        throw new JobExecutionException(exception, true);
    }

}
