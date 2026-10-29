package cn.iocoder.yudao.framework.quartz.core.handler;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Job 处理器注册表，兼容新的 {@link JobHandler} 和旧的 {@link XxlJob} 方法。
 */
@RequiredArgsConstructor
public class JobHandlerRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, XxlJobMethod> xxlJobMethods = new ConcurrentHashMap<>();
    private volatile boolean xxlJobMethodsLoaded;

    public boolean exists(String jobHandlerName) {
        if (getJobHandler(jobHandlerName) != null) {
            return true;
        }
        return getXxlJobMethod(jobHandlerName) != null;
    }

    public String execute(Long jobId, String jobHandlerName, String jobHandlerParam) throws Exception {
        JobHandler jobHandler = getJobHandler(jobHandlerName);
        if (jobHandler != null) {
            return jobHandler.execute(jobHandlerParam);
        }
        XxlJobMethod xxlJobMethod = getXxlJobMethod(jobHandlerName);
        Assert.notNull(xxlJobMethod, "JobHandler({}) 不存在", jobHandlerName);
        return xxlJobMethod.invoke(jobId, jobHandlerParam);
    }

    private JobHandler getJobHandler(String jobHandlerName) {
        if (!applicationContext.containsBean(jobHandlerName)) {
            return null;
        }
        Object bean = applicationContext.getBean(jobHandlerName);
        return bean instanceof JobHandler handler ? handler : null;
    }

    private XxlJobMethod getXxlJobMethod(String jobHandlerName) {
        loadXxlJobMethodsIfNecessary();
        return xxlJobMethods.get(jobHandlerName);
    }

    private void loadXxlJobMethodsIfNecessary() {
        if (xxlJobMethodsLoaded) {
            return;
        }
        synchronized (this) {
            if (xxlJobMethodsLoaded) {
                return;
            }
            applicationContext.getBeansWithAnnotation(Component.class).forEach((beanName, bean) -> {
                Class<?> targetClass = AopUtils.getTargetClass(bean);
                Map<Method, XxlJob> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
                        (MethodIntrospector.MetadataLookup<XxlJob>) method ->
                                AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class));
                annotatedMethods.forEach((method, xxlJob) -> xxlJobMethods.put(xxlJob.value(), new XxlJobMethod(bean, method)));
                if (annotatedMethods.size() == 1) {
                    xxlJobMethods.putIfAbsent(beanName, new XxlJobMethod(bean, annotatedMethods.keySet().iterator().next()));
                }
            });
            xxlJobMethodsLoaded = true;
        }
    }

    private record XxlJobMethod(Object bean, Method method) {

        private String invoke(Long jobId, String jobHandlerParam) throws Exception {
            XxlJobContext oldContext = XxlJobContext.getXxlJobContext();
            XxlJobContext xxlJobContext = new XxlJobContext(jobId == null ? 0L : jobId, jobHandlerParam, "", 0, 1);
            XxlJobContext.setXxlJobContext(xxlJobContext);
            try {
                ReflectionUtils.makeAccessible(method);
                Object result = method.getParameterCount() == 0 ? method.invoke(bean) : method.invoke(bean, jobHandlerParam);
                if (xxlJobContext.getHandleCode() != XxlJobContext.HANDLE_CODE_SUCCESS) {
                    throw new IllegalStateException(xxlJobContext.getHandleMsg());
                }
                return result == null ? StrUtil.toStringOrNull(xxlJobContext.getHandleMsg()) : StrUtil.toStringOrNull(result);
            } catch (InvocationTargetException ex) {
                Throwable targetException = ex.getTargetException();
                if (targetException instanceof Exception exception) {
                    throw exception;
                }
                throw ex;
            } finally {
                XxlJobContext.setXxlJobContext(oldContext);
            }
        }

    }

}
