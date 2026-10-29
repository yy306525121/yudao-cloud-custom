package cn.iocoder.yudao.framework.quartz.config;

import cn.iocoder.yudao.framework.quartz.core.scheduler.SchedulerManager;
import com.xxl.job.core.executor.XxlJobExecutor;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import static org.assertj.core.api.Assertions.assertThat;

class JobConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(YudaoQuartzAutoConfiguration.class,
                    YudaoXxlJobAutoConfiguration.class))
            .withPropertyValues("spring.quartz.auto-startup=false", "xxl.job.admin.addresses=http://localhost",
                    "xxl.job.executor.appname=test", "xxl.job.executor.logpath=/tmp/yudao-job-test");

    @Test
    void executesThroughSpringInjectedJobFactory() {
        java.util.concurrent.CountDownLatch executed = new java.util.concurrent.CountDownLatch(1);
        runner.withBean("testJob", cn.iocoder.yudao.framework.quartz.core.handler.JobHandler.class,
                () -> param -> { executed.countDown(); return param; })
                .withPropertyValues("yudao.job.type=quartz").run(context -> {
                    Scheduler scheduler = context.getBean(Scheduler.class);
                    SchedulerManager manager = context.getBean(SchedulerManager.class);
                    manager.addJob(1L, "testJob", "param", "0 0 0 1 1 ? 2099", 0, 0);
                    scheduler.start();
                    manager.triggerJob(1L, "testJob", "param");
                    assertThat(executed.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                });
    }

    @Test
    void quartzDoesNotStartXxl() {
        runner.withPropertyValues("yudao.job.type=quartz").run(context -> {
            assertThat(context).hasSingleBean(Scheduler.class).hasSingleBean(SchedulerManager.class);
            assertThat(context).doesNotHaveBean(XxlJobExecutor.class);
        });
    }

    @Test
    void noneDoesNotCreateScheduler() {
        runner.withPropertyValues("yudao.job.type=none").run(context -> {
            assertThat(context).doesNotHaveBean(Scheduler.class).doesNotHaveBean(XxlJobExecutor.class);
            assertThat(context).hasSingleBean(SchedulerManager.class);
        });
    }

    @Test
    void xxlUsesExecutorOnly() {
        runner.withBean(XxlJobExecutor.class, () -> org.mockito.Mockito.mock(XxlJobExecutor.class))
                .withPropertyValues("yudao.job.type=xxl").run(context -> {
                    assertThat(context).hasSingleBean(XxlJobExecutor.class).doesNotHaveBean(Scheduler.class);
                });
    }

    @Test
    void schedulerManagerIgnoresForeignScheduler() {
        runner.withBean("iotScheduler", Scheduler.class, () -> org.mockito.Mockito.mock(Scheduler.class))
                .withPropertyValues("yudao.job.type=none").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(org.springframework.test.util.ReflectionTestUtils.getField(
                            context.getBean(SchedulerManager.class), "scheduler")).isNull();
                });
    }

    @Test
    void filtersBootFallback() {
        assertThat(new QuartzAutoConfigurationFilter().match(new String[]{null,
                "org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration", "other"}, null))
                .containsExactly(true, false, true);
    }
}
