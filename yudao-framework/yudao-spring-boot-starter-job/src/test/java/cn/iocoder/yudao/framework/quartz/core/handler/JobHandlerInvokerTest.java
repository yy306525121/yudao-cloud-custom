package cn.iocoder.yudao.framework.quartz.core.handler;

import cn.iocoder.yudao.framework.quartz.core.enums.JobDataKeyEnum;
import cn.iocoder.yudao.framework.quartz.core.service.JobLogFrameworkService;
import cn.iocoder.yudao.framework.quartz.core.util.JobTraceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobHandlerInvokerTest {

    private static final Long JOB_ID = 100L;
    private static final String JOB_HANDLER_NAME = "demoJob";
    private static final String JOB_HANDLER_PARAM = "param";

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void executeInternal_shouldCreateJobTraceIdAndClearAfterExecution() throws Exception {
        TestJobHandlerInvoker invoker = createInvoker(traceId -> {
            assertTrue(traceId.startsWith("job-" + JOB_ID + "-"));
            assertTrue(traceId.length() > ("job-" + JOB_ID + "-").length());
        });

        invoker.executeInternal(mockJobExecutionContext());

        assertNull(MDC.get(JobTraceUtils.MDC_KEY_TRACE_ID));
    }

    @Test
    void executeInternal_shouldKeepExistingTraceIdAndRestoreAfterExecution() throws Exception {
        String oldTraceId = "request-trace-id";
        MDC.put(JobTraceUtils.MDC_KEY_TRACE_ID, oldTraceId);
        TestJobHandlerInvoker invoker = createInvoker(traceId -> assertEquals(oldTraceId, traceId));

        invoker.executeInternal(mockJobExecutionContext());

        assertEquals(oldTraceId, MDC.get(JobTraceUtils.MDC_KEY_TRACE_ID));
    }

    @Test
    void failureRetriesAndRestoresTrace() throws Exception {
        TestJobHandlerInvoker invoker = createInvoker(traceId -> {});
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        when(registry.execute(JOB_ID, JOB_HANDLER_NAME, JOB_HANDLER_PARAM))
                .thenThrow(new IllegalStateException("failed"));
        ReflectionTestUtils.setField(invoker, "jobHandlerRegistry", registry);
        JobExecutionContext context = mockJobExecutionContext();
        context.getMergedJobDataMap().put(JobDataKeyEnum.JOB_RETRY_COUNT.name(), 1);
        org.quartz.JobExecutionException failure = assertThrows(org.quartz.JobExecutionException.class,
                () -> invoker.executeInternal(context));
        assertTrue(failure.refireImmediately());
        assertNull(MDC.get(JobTraceUtils.MDC_KEY_TRACE_ID));
        JobLogFrameworkService logs = (JobLogFrameworkService) ReflectionTestUtils.getField(invoker, "jobLogFrameworkService");
        verify(logs).updateJobLogResultAsync(eq(200L), any(), any(), eq(false), eq("IllegalStateException: failed"));
        when(context.getRefireCount()).thenReturn(1);
        MDC.put(JobTraceUtils.MDC_KEY_TRACE_ID, "existing");
        failure = assertThrows(org.quartz.JobExecutionException.class, () -> invoker.executeInternal(context));
        assertEquals(false, failure.refireImmediately());
        assertEquals("existing", MDC.get(JobTraceUtils.MDC_KEY_TRACE_ID));
    }

    private static TestJobHandlerInvoker createInvoker(TraceIdAssert traceIdAssert) throws Exception {
        JobLogFrameworkService jobLogFrameworkService = mock(JobLogFrameworkService.class);
        when(jobLogFrameworkService.createJobLog(eq(JOB_ID), any(), eq(JOB_HANDLER_NAME), eq(JOB_HANDLER_PARAM), eq(1)))
                .thenReturn(200L);

        JobHandlerRegistry jobHandlerRegistry = mock(JobHandlerRegistry.class);
        when(jobHandlerRegistry.execute(eq(JOB_ID), eq(JOB_HANDLER_NAME), eq(JOB_HANDLER_PARAM))).thenAnswer(invocation -> {
            traceIdAssert.accept(MDC.get(JobTraceUtils.MDC_KEY_TRACE_ID));
            return "success";
        });

        TestJobHandlerInvoker invoker = new TestJobHandlerInvoker();
        ReflectionTestUtils.setField(invoker, "jobLogFrameworkService", jobLogFrameworkService);
        ReflectionTestUtils.setField(invoker, "jobHandlerRegistry", jobHandlerRegistry);
        return invoker;
    }

    private static JobExecutionContext mockJobExecutionContext() {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(JobDataKeyEnum.JOB_ID.name(), JOB_ID);
        jobDataMap.put(JobDataKeyEnum.JOB_HANDLER_NAME.name(), JOB_HANDLER_NAME);
        jobDataMap.put(JobDataKeyEnum.JOB_HANDLER_PARAM.name(), JOB_HANDLER_PARAM);
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(jobDataMap);
        return context;
    }

    private static class TestJobHandlerInvoker extends JobHandlerInvoker {
        @Override
        public void executeInternal(JobExecutionContext executionContext) throws org.quartz.JobExecutionException {
            super.executeInternal(executionContext);
        }
    }

    private interface TraceIdAssert {
        void accept(String traceId);
    }

}
