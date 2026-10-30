package cn.iocoder.yudao.framework.jasypt.bjca.config;

import cn.iocoder.yudao.framework.jasypt.bjca.core.*;
import com.ulisesbocchio.jasyptspringboot.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.ConfigurableEnvironment;

@AutoConfiguration(beforeName = "com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration")
@EnableConfigurationProperties(MscpProperties.class)
public class BjcaAutoConfiguration {

    // Each context needs a resolver bound to its own Environment: the bootstrap parent
    // cannot see application properties loaded later into the child context.
    @Bean(name = "encryptablePropertyDetector")
    @ConditionalOnMissingBean(value = EncryptablePropertyDetector.class, name = "encryptablePropertyDetector", search = SearchStrategy.CURRENT)
    public EncryptablePropertyDetector encryptablePropertyDetector() {
        return new BjcaPropertyDetector();
    }

    @Bean(name = "encryptablePropertyResolver")
    @ConditionalOnMissingBean(value = EncryptablePropertyResolver.class, name = "encryptablePropertyResolver", search = SearchStrategy.CURRENT)
    public EncryptablePropertyResolver encryptablePropertyResolver(
            @Qualifier("encryptablePropertyDetector") EncryptablePropertyDetector detector,
            ConfigurableEnvironment environment) {
        return new BjcaPropertyResolver(detector, environment);
    }
}
