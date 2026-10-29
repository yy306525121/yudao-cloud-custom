package cn.iocoder.yudao.framework.tracer.config;

import cn.iocoder.yudao.framework.tracer.core.filter.TraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class YudaoTracerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(YudaoTracerAutoConfiguration.class));

    @Test
    void traceFilter_shouldRegisterWithoutSkyWalkingOpenTracingToolkit() {
        contextRunner.withClassLoader(new FilteredClassLoader("org.apache.skywalking.apm.toolkit.opentracing"))
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context.getBean(FilterRegistrationBean.class).getFilter()).isInstanceOf(TraceFilter.class);
                });
    }

    @Test
    void traceFilter_shouldNotRegisterWhenDisabled() {
        contextRunner.withPropertyValues("yudao.tracer.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

}
