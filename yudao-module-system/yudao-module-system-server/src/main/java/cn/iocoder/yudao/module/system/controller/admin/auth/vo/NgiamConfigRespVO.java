package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "管理后台 - IAM 登录公开配置")
public class NgiamConfigRespVO {
    private boolean enabled;
    private String service;
}
