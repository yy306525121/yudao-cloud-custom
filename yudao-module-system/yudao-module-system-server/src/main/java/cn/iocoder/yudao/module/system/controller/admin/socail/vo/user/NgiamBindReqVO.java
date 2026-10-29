package cn.iocoder.yudao.module.system.controller.admin.socail.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "管理后台 - IAM 账户预绑定")
public class NgiamBindReqVO {
    @NotNull
    private Long userId;
    @NotBlank
    @Size(max = 32)
    @Schema(description = "IAM 账户名，精确匹配，不是回调 accountId")
    private String iamUsername;
}
