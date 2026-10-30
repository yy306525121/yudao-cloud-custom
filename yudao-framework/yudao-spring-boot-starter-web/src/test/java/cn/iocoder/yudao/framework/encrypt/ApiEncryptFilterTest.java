package cn.iocoder.yudao.framework.encrypt;

import cn.iocoder.yudao.framework.encrypt.core.FieldCrypto;
import cn.iocoder.yudao.framework.encrypt.core.annotation.ApiEncrypt;
import cn.iocoder.yudao.framework.encrypt.core.filter.ApiEncryptFilter;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ApiEncryptFilterTest {
    @RestController
    @ApiEncrypt(requestFields = "mobile", responseFields = "data.mobile")
    static class Controller {
        @PostMapping("/admin-api/field-test")
        public CommonResult<Body> echo(@Valid @RequestBody Body body) { return CommonResult.success(body); }
        @GetMapping("/admin-api/bad-response")
        @ApiEncrypt(responseFields = "data.mobile")
        public CommonResult<?> invalidResponse() {
            return CommonResult.success(java.util.Map.of("mobile", 123, "name", "不应泄漏"));
        }
        @PostMapping("/admin-api/plain-test")
        @ApiEncrypt
        public CommonResult<Body> plain(@RequestBody Body body) { return CommonResult.success(body); }
    }
    static class Body {
        @Pattern(regexp = "1[0-9]{10}")
        public String mobile;
        public String name;
    }
    @Test void decryptBeforeValidationAndEncryptOnlySelectedFields() throws Exception {
        var properties = ApiEncryptTest.properties("SM4"); var crypto = new FieldCrypto(properties);
        var controller = new Controller();
        MockMvc initial = MockMvcBuilders.standaloneSetup(controller).build();
        var mapping = initial.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerMapping.class);
        var errors = mock(GlobalExceptionHandler.class);
        when(errors.allExceptionHandler(any(), any())).thenReturn(CommonResult.error(400, "字段解密失败"));
        var filter = new ApiEncryptFilter(new WebProperties(), properties, mapping, errors);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(filter).build();
        var json = new ObjectMapper();
        String body = json.writeValueAsString(java.util.Map.of("mobile", crypto.encrypt("13800138000"), "name", "张三"));
        var response = mvc.perform(post("/admin-api/field-test").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        var result = json.readTree(response.getContentAsByteArray());
        assertEquals(200, response.getStatus()); assertEquals(0, result.get("code").intValue());
        assertEquals("张三", result.at("/data/name").textValue());
        assertEquals("13800138000", crypto.decrypt(result.at("/data/mobile").textValue()));
        assertNull(response.getHeader("X-Api-Encrypt"));
        var bad = mvc.perform(get("/admin-api/bad-response")).andReturn().getResponse();
        assertEquals(400, json.readTree(bad.getContentAsByteArray()).get("code").intValue());
        assertFalse(bad.getContentAsString().contains("不应泄漏"));
        var form = mvc.perform(post("/admin-api/field-test").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("mobile=13800138000")).andReturn().getResponse();
        assertEquals(400, json.readTree(form.getContentAsByteArray()).get("code").intValue());
        assertEquals(response.getContentAsByteArray().length, response.getContentLength());
        var invalid = mvc.perform(post("/admin-api/field-test").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("mobile", crypto.encrypt("invalid"))))).andReturn().getResponse();
        assertEquals(400, invalid.getStatus());
        var plaintext = mvc.perform(post("/admin-api/field-test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"13800138000\"}")).andReturn().getResponse();
        assertEquals(400, json.readTree(plaintext.getContentAsByteArray()).get("code").intValue());
        var plain = mvc.perform(post("/admin-api/plain-test").contentType(MediaType.APPLICATION_JSON)
                .header("X-Api-Encrypt", "true").content("{\"mobile\":\"13800138000\"}")).andReturn().getResponse();
        assertEquals("13800138000", json.readTree(plain.getContentAsByteArray()).at("/data/mobile").textValue());
    }
    public static void main(String[] args) throws Exception {
        new ApiEncryptFilterTest().decryptBeforeValidationAndEncryptOnlySelectedFields();
        System.out.println("MockMvc field filter and validation checks passed");
    }
}
