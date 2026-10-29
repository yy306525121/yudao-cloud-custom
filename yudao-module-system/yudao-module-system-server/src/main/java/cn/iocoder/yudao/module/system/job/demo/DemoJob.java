package cn.iocoder.yudao.module.system.job.demo;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class DemoJob {

    @XxlJob("demoJob")
    @TenantJob
    public void execute() {
        Long tenantId = TenantContextHolder.getTenantId();
        System.out.println("美滋滋" + tenantId);
    }

}
