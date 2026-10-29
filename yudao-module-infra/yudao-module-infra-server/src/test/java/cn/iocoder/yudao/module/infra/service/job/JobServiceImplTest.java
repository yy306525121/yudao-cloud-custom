package cn.iocoder.yudao.module.infra.service.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandlerRegistry;
import cn.iocoder.yudao.framework.quartz.core.scheduler.SchedulerManager;
import cn.iocoder.yudao.module.infra.controller.admin.job.vo.job.JobSaveReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.job.JobDO;
import cn.iocoder.yudao.module.infra.dal.mysql.job.JobMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class JobServiceImplTest {
    private final JobMapper mapper = mock(JobMapper.class);
    private final SchedulerManager scheduler = mock(SchedulerManager.class);
    private final JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
    private final JobServiceImpl service = new JobServiceImpl();

    JobServiceImplTest() {
        ReflectionTestUtils.setField(service, "jobMapper", mapper);
        ReflectionTestUtils.setField(service, "schedulerManager", scheduler);
        ReflectionTestUtils.setField(service, "jobHandlerRegistry", registry);
    }

    private JobSaveReqVO request() {
        JobSaveReqVO vo = new JobSaveReqVO();
        vo.setId(1L); vo.setName("demo"); vo.setHandlerName("demoJob");
        vo.setHandlerParam("param"); vo.setCronExpression("0 * * * * ?");
        vo.setRetryCount(1); vo.setRetryInterval(100);
        return vo;
    }

    private JobDO job(int status) {
        return JobDO.builder().id(1L).handlerName("demoJob").status(status)
                .handlerParam("param").cronExpression("0 * * * * ?")
                .retryCount(1).retryInterval(100).build();
    }

    @Test
    void createValidatesAndSchedules() throws Exception {
        when(registry.exists("demoJob")).thenReturn(true);
        doAnswer(invocation -> { ((JobDO) invocation.getArgument(0)).setId(1L); return 1; })
                .when(mapper).insert(any(JobDO.class));
        assertThat(service.createJob(request())).isEqualTo(1L);
        verify(scheduler).addJob(1L, "demoJob", "param", "0 * * * * ?", 1, 100);
        verify(mapper).updateById(argThat((JobDO job) -> job.getStatus() == 1));
    }

    @Test
    void rejectsInvalidCronAndMissingHandler() {
        JobSaveReqVO vo = request(); vo.setCronExpression("invalid");
        assertThatThrownBy(() -> service.createJob(vo)).hasMessageContaining("CRON");
        assertThatThrownBy(() -> service.createJob(request())).hasMessageContaining("Bean");
        verifyNoInteractions(scheduler);
    }

    @Test
    void updateStatusTriggerAndDelete() throws Exception {
        when(mapper.selectById(1L)).thenReturn(job(1));
        when(registry.exists("demoJob")).thenReturn(true);
        service.updateJob(request());
        verify(scheduler).updateJob("demoJob", "param", "0 * * * * ?", 1, 100);
        service.updateJobStatus(1L, 2);
        verify(scheduler).pauseJob("demoJob");
        when(mapper.selectById(1L)).thenReturn(job(2));
        service.updateJobStatus(1L, 1);
        verify(scheduler).resumeJob("demoJob");
        service.triggerJob(1L);
        verify(scheduler).triggerJob(1L, "demoJob", "param");
        assertThatThrownBy(() -> service.updateJob(request())).hasMessageContaining("开启状态");
        service.deleteJob(1L);
        verify(scheduler).deleteJob("demoJob");
    }

    @Test
    void syncPreservesStoppedStatusAndBatchDeletes() throws Exception {
        when(mapper.selectList()).thenReturn(List.of(job(2)));
        service.syncJob();
        var ordered = inOrder(scheduler);
        ordered.verify(scheduler).deleteJob("demoJob");
        ordered.verify(scheduler).addJob(1L, "demoJob", "param", "0 * * * * ?", 1, 100);
        ordered.verify(scheduler).pauseJob("demoJob");
        when(mapper.selectByIds(List.of(1L))).thenReturn(List.of(job(2)));
        service.deleteJobList(List.of(1L));
        verify(mapper).deleteByIds(List.of(1L));
    }
}
