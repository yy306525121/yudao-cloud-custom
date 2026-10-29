package cn.iocoder.yudao.module.infra.service.job;

import cn.iocoder.yudao.module.infra.dal.mysql.job.JobLogMapper;
import cn.iocoder.yudao.module.infra.dal.dataobject.job.JobLogDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class JobLogServiceImplTest {
    @Test
    void createsFinishesAndCleansInBatches() {
        JobLogMapper mapper = mock(JobLogMapper.class);
        JobLogServiceImpl service = new JobLogServiceImpl();
        ReflectionTestUtils.setField(service, "jobLogMapper", mapper);
        doAnswer(invocation -> { ((JobLogDO) invocation.getArgument(0)).setId(2L); return 1; })
                .when(mapper).insert(any(JobLogDO.class));
        LocalDateTime now = LocalDateTime.now();
        assertThat(service.createJobLog(1L, now, "demo", "param", 1)).isEqualTo(2L);
        service.updateJobLogResultAsync(2L, now, 100, false, "failed");
        verify(mapper).updateById(argThat((JobLogDO log) -> log.getStatus() == 2
                && log.getDuration() == 100 && "failed".equals(log.getResult())));
        when(mapper.deleteByCreateTimeLt(any(), eq(100))).thenReturn(100, 3);
        assertThat(service.cleanJobLog(14, 100)).isEqualTo(103);
        verify(mapper, times(2)).deleteByCreateTimeLt(any(), eq(100));
    }
}
