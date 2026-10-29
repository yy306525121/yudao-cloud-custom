package cn.iocoder.yudao.framework.quartz.config;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandlerRegistry;
import cn.iocoder.yudao.framework.quartz.core.scheduler.SchedulerManager;
import cn.iocoder.yudao.framework.quartz.core.service.JobLogFrameworkService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.JobStoreType;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Quartz 自动配置类。
 */
@AutoConfiguration
@EnableConfigurationProperties(QuartzProperties.class)
@Slf4j
public class YudaoQuartzAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "yudao.job", name = "type", havingValue = "quartz")
    @ConditionalOnMissingBean(name = "schedulerFactoryBean")
    public SchedulerFactoryBean schedulerFactoryBean(QuartzProperties properties, ObjectProvider<DataSource> dataSource,
                                                     ObjectProvider<PlatformTransactionManager> transactionManager,
                                                     ApplicationContext applicationContext) {
        SchedulerFactoryBean factoryBean = new SchedulerFactoryBean();
        factoryBean.setSchedulerName(properties.getSchedulerName());
        factoryBean.setAutoStartup(properties.isAutoStartup());
        factoryBean.setWaitForJobsToCompleteOnShutdown(properties.isWaitForJobsToCompleteOnShutdown());
        factoryBean.setOverwriteExistingJobs(properties.isOverwriteExistingJobs());
        factoryBean.setStartupDelay((int) properties.getStartupDelay().getSeconds());
        factoryBean.setJobFactory(new AutowireCapableBeanJobFactory(applicationContext.getAutowireCapableBeanFactory()));

        Properties quartzProperties = new Properties();
        quartzProperties.putAll(properties.getProperties());
        factoryBean.setQuartzProperties(quartzProperties);

        if (JobStoreType.JDBC.equals(properties.getJobStoreType())) {
            dataSource.ifAvailable(factoryBean::setDataSource);
            transactionManager.ifAvailable(factoryBean::setTransactionManager);
        }
        return factoryBean;
    }

    @Bean
    public SchedulerManager schedulerManager(@Qualifier("schedulerFactoryBean") Optional<Scheduler> scheduler) {
        if (scheduler.isEmpty()) {
            log.info("[定时任务 - Quartz 已禁用][可配置 yudao.job.type=quartz 开启]");
            return new SchedulerManager(null);
        }
        return new SchedulerManager(scheduler.get());
    }

    @Bean
    public JobHandlerRegistry jobHandlerRegistry(ApplicationContext applicationContext) {
        return new JobHandlerRegistry(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobLogFrameworkService jobLogFrameworkService() {
        return new JobLogFrameworkService() {
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "yudao.job", name = "type", havingValue = "quartz")
    @EnableScheduling // 开启 Spring 自带的定时任务
    static class SchedulingConfiguration {
    }

    /**
     * Quartz Job 由 Quartz 自行实例化，需要手动接回 Spring 的依赖注入。
     */
    static class AutowireCapableBeanJobFactory extends SpringBeanJobFactory {

        private final AutowireCapableBeanFactory beanFactory;

        AutowireCapableBeanJobFactory(AutowireCapableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(Objects.requireNonNull(job));
            return job;
        }

    }

}
