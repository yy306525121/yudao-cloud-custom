package cn.iocoder.yudao.module.system.controller.admin.social.vo.client;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@Schema(description = "管理后台 - 社交客户端创建/修改 Request VO")
@Data
public class SocialClientSaveReqVO {

    @Schema(description = "编号", example = "27162")
    private Long id;

    @Schema(description = "应用名", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao商城")
    @NotNull(message = "应用名不能为空")
    private String name;

    @Schema(description = "社交平台的类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "31")
    @NotNull(message = "社交平台的类型不能为空")
    @InEnum(SocialTypeEnum.class)
    private Integer socialType;

    @Schema(description = "用户类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "用户类型不能为空")
    @InEnum(UserTypeEnum.class)
    private Integer userType;

    @Schema(description = "客户端编号，CAS 可选记录应用编码（不参与登录）", example = "ljsc")
    private String clientId;

    @Schema(description = "客户端密钥，CAS 不需要，其他平台必填", example = "peter")
    private String clientSecret;

    @Schema(description = "授权方的网页应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000045")
    private String agentId;

    @Schema(description = "publicKey 公钥", example = "2000045")
    private String publicKey;

    @Schema(description = "IAM 对接地址，仅协议、主机及可选端口，例如 http://10.1.102.64:80")
    private String casBaseUrl;
    @Schema(description = "CAS 注册的固定前端回调地址")
    private String casService;
    @Schema(description = "CAS 请求超时毫秒，默认 5000，范围 100 到 60000")
    private Integer casTimeoutMillis;


    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "状态不能为空")
    @InEnum(CommonStatusEnum.class)
    private Integer status;

    @AssertTrue(message = "客户端编号不能为空")
    @JsonIgnore
    public boolean isClientIdValid() {
        return Objects.equals(socialType, SocialTypeEnum.NGIAM_CAS.getType()) || clientId != null;
    }

    @AssertTrue(message = "客户端密钥不能为空")
    @JsonIgnore
    public boolean isClientSecretValid() {
        return Objects.equals(socialType, SocialTypeEnum.NGIAM_CAS.getType()) || clientSecret != null;
    }

    @AssertTrue(message = "CAS 仅支持管理端，且必须填写有效的对接地址、固定回调地址及超时（100-60000 毫秒）")
    @JsonIgnore
    public boolean isCasConfigValid() {
        if (!Objects.equals(socialType, SocialTypeEnum.NGIAM_CAS.getType())) {
            return true;
        }
        return Objects.equals(userType, UserTypeEnum.ADMIN.getValue())
                && cn.iocoder.yudao.module.system.framework.justauth.core.ngiam.NgiamCasConfig.isBaseUrl(casBaseUrl)
                && validCasUrl(casService)
                && (casTimeoutMillis == null || (casTimeoutMillis >= 100 && casTimeoutMillis <= 60000));
    }

    private boolean validCasUrl(String url) {
        return url != null && url.length() <= 2048
                && cn.iocoder.yudao.module.system.framework.justauth.core.ngiam.NgiamCasConfig.isHttpUrl(url);
    }

    @AssertTrue(message = "agentId 不能为空")
    @JsonIgnore
    public boolean isAgentIdValid() {
        // 如果是企业微信，必须填写 agentId 属性
        return !Objects.equals(socialType, SocialTypeEnum.WECHAT_ENTERPRISE.getType())
                || !StrUtil.isEmpty(agentId);
    }

    @AssertTrue(message = "publicKey 不能为空")
    @JsonIgnore
    public boolean isPublicKeyValid() {
        // 如果是支付宝，必须填写 publicKey 属性
        return !Objects.equals(socialType, SocialTypeEnum.ALIPAY_MINI_PROGRAM.getType())
                || !StrUtil.isEmpty(publicKey);
    }

}
