package cn.iocoder.yudao.framework.encrypt.core.annotation;

import java.lang.annotation.*;

/**
 * HTTP API 加解密注解
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiEncrypt {

    /** 相对于 JSON 请求体的字符串字段路径，例如 mobile、items[*].mobile。 */
    String[] requestFields() default {};

    /** 相对于完整 JSON 响应的字段路径，例如 data.mobile。 */
    String[] responseFields() default {};

}
