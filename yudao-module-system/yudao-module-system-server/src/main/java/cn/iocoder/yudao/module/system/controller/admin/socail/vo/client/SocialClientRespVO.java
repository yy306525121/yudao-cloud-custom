package cn.iocoder.yudao.module.system.controller.admin.socail.vo.client;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 社交客户端 Response VO")
@Data
public class SocialClientRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "27162")
    private Long id;

    @Schema(description = "应用名", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao商城")
    private String name;

    @Schema(description = "社交平台的类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "31")
    private Integer socialType;

    @Schema(description = "用户类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer userType;

    @Schema(description = "客户端编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "wwd411c69a39ad2e54")
    private String clientId;

    @Schema(description = "客户端密钥", requiredMode = Schema.RequiredMode.REQUIRED, example = "peter")
    private String clientSecret;

    @Schema(description = "授权方的网页应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000045")
    private String agentId;

    @Schema(description = "IAM 对接地址，仅协议、主机及可选端口，例如 http://10.1.102.64:80")
    private String casBaseUrl;
    @Schema(description = "CAS 注册的固定前端回调地址")
    private String casService;
    @Schema(description = "CAS 请求超时毫秒，默认 5000，范围 100 到 60000")
    private Integer casTimeoutMillis;


    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
