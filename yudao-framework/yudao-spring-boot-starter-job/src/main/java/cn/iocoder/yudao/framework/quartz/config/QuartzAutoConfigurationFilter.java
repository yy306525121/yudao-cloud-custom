package cn.iocoder.yudao.framework.quartz.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

/** 使用本 starter 的配置统一控制调度器，防止 Boot 在 xxl/none 模式创建默认 Quartz。 */
public class QuartzAutoConfigurationFilter implements AutoConfigurationImportFilter {
    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata metadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < matches.length; i++) {
            matches[i] = !"org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration"
                    .equals(autoConfigurationClasses[i]);
        }
        return matches;
    }
}
