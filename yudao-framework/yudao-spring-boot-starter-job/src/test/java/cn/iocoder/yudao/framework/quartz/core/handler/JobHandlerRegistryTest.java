package cn.iocoder.yudao.framework.quartz.core.handler;

import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class JobHandlerRegistryTest {
    @Test
    void annotationHandlerUsesProxyParametersAndRestoresContext() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            AtomicBoolean intercepted = new AtomicBoolean();
            ProxyFactory factory = new ProxyFactory(new LegacyJob());
            factory.setProxyTargetClass(true);
            factory.addAdvice((MethodInterceptor) invocation -> {
                intercepted.set(true);
                return invocation.proceed();
            });
            context.registerBean("legacyJob", LegacyJob.class, () -> (LegacyJob) factory.getProxy());
            context.refresh();
            JobHandlerRegistry registry = new JobHandlerRegistry(context);
            XxlJobContext old = new XxlJobContext(2, "old", "", 0, 1);
            XxlJobContext.setXxlJobContext(old);
            try {
                assertThat(registry.exists("legacyJob")).isTrue();
                assertThat(registry.execute(1L, "legacyJob", "input")).isEqualTo("input");
                assertThat(intercepted).isTrue();
                assertThatThrownBy(() -> registry.execute(1L, "failJob", "input"))
                        .hasMessage("failed");
                assertThat(XxlJobContext.getXxlJobContext()).isSameAs(old);
            } finally {
                XxlJobContext.setXxlJobContext(null);
            }
        }
    }

    @Test
    void supportsJobHandlerAndMissingNames() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("newJob", JobHandler.class, () -> param -> param);
            context.refresh();
            JobHandlerRegistry registry = new JobHandlerRegistry(context);
            assertThat(registry.execute(1L, "newJob", "input")).isEqualTo("input");
            assertThat(registry.exists("missing")).isFalse();
        }
    }

    @Component
    static class LegacyJob {
        @XxlJob("legacyJob")
        public String execute() { return XxlJobHelper.getJobParam(); }
        @XxlJob("failJob")
        public void fail() { XxlJobHelper.handleFail("failed"); }
    }
}
