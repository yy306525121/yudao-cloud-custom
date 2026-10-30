package cn.iocoder.yudao.framework.encrypt.core.filter;

import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.encrypt.config.ApiEncryptProperties;
import cn.iocoder.yudao.framework.encrypt.core.FieldCrypto;
import cn.iocoder.yudao.framework.encrypt.core.annotation.ApiEncrypt;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.filter.ApiRequestFilter;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/**
 * API 加密过滤器，处理 {@link ApiEncrypt} 注解。
 *
 * 1. 解密请求参数
 * 2. 加密响应结果
 *
 * 疑问：为什么不使用 SpringMVC 的 RequestBodyAdvice 或 ResponseBodyAdvice 机制呢？
 * 回答：考虑到项目中会记录访问日志、异常日志，以及 HTTP API 签名等场景，最好是全局级、且提前做解析！！！
 *
 * @author 芋道源码
 */
@Slf4j
public class ApiEncryptFilter extends ApiRequestFilter {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    private final GlobalExceptionHandler globalExceptionHandler;

    private final FieldCrypto crypto;

    public ApiEncryptFilter(WebProperties webProperties, ApiEncryptProperties properties,
                            RequestMappingHandlerMapping mapping, GlobalExceptionHandler handler) {
        super(webProperties);
        this.requestMappingHandlerMapping = mapping;
        this.globalExceptionHandler = handler;
        this.crypto = new FieldCrypto(properties);
    }

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ApiEncrypt annotation = getApiEncrypt(request);
        if (annotation == null || (annotation.requestFields().length == 0 && annotation.responseFields().length == 0)) {
            chain.doFilter(request, response);
            return;
        }
        if (annotation.requestFields().length > 0) {
            try {
                String type = request.getContentType();
                if (type == null || !(type.toLowerCase(java.util.Locale.ROOT).contains("application/json")
                        || type.toLowerCase(java.util.Locale.ROOT).contains("+json"))) {
                    throw new IllegalArgumentException("字段解密仅支持 JSON 请求体");
                }
                request = new ApiDecryptRequestWrapper(request, crypto, annotation.requestFields());
            } catch (Exception ex) {
                ServletUtils.writeJSON(response, globalExceptionHandler.allExceptionHandler(request,
                        invalidParamException("请求字段解密失败，请检查字段类型、密文和加密配置")));
                return;
            }
        }
        if (annotation.responseFields().length == 0) {
            chain.doFilter(request, response);
            return;
        }
        ApiEncryptResponseWrapper wrapper = new ApiEncryptResponseWrapper(response);
        chain.doFilter(request, wrapper);
        try {
            wrapper.encrypt(crypto, annotation.responseFields());
        } catch (Exception ex) {
            response.resetBuffer();
            response.setHeader("Content-Length", null);
            ServletUtils.writeJSON(response, globalExceptionHandler.allExceptionHandler(request,
                    new IllegalStateException("响应字段加密失败")));
        }
    }

    /**
     * 获取 @ApiEncrypt 注解
     *
     * @param request 请求
     */
    @SuppressWarnings("PatternVariableCanBeUsed")
    private ApiEncrypt getApiEncrypt(HttpServletRequest request) {
        try {
            // 特殊：兼容 SpringBoot 2.X 版本会报错的问题 https://t.zsxq.com/kqyiB
            if (!ServletRequestPathUtils.hasParsedRequestPath(request)) {
                ServletRequestPathUtils.parseAndCache(request);
            }

            // 解析 @ApiEncrypt 注解
            HandlerExecutionChain mappingHandler = requestMappingHandlerMapping.getHandler(request);
            if (mappingHandler == null) {
                return null;
            }
            Object handler = mappingHandler.getHandler();
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                ApiEncrypt annotation = handlerMethod.getMethodAnnotation(ApiEncrypt.class);
                if (annotation == null) {
                    annotation = handlerMethod.getBeanType().getAnnotation(ApiEncrypt.class);
                }
                return annotation;
            }
        } catch (Exception e) {
            log.error("[getApiEncrypt][url({}/{}) 获取 @ApiEncrypt 注解失败]",
                    request.getRequestURI(), request.getMethod(), e);
        }
        return null;
    }

}
