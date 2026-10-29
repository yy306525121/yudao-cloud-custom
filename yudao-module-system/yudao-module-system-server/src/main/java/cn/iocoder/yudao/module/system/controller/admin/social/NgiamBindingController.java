package cn.iocoder.yudao.module.system.controller.admin.socail;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.socail.vo.user.*;
import cn.iocoder.yudao.module.system.service.social.NgiamBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - IAM 账户绑定")
@RestController
@RequestMapping("/system/ngiam-binding")
@Validated
public class NgiamBindingController {
    @Resource private NgiamBindingService service;

    @GetMapping("/get")
    @Operation(summary = "查询用户 IAM 绑定")
    @PreAuthorize("@ss.hasPermission('system:ngiam-binding:query')")
    public CommonResult<NgiamBindRespVO> get(@RequestParam("userId") Long userId) {
        return success(service.get(userId));
    }

    @PostMapping("/bind")
    @Operation(summary = "管理员预绑定 IAM 账户")
    @PreAuthorize("@ss.hasPermission('system:ngiam-binding:update')")
    public CommonResult<Boolean> bind(@RequestBody @Valid NgiamBindReqVO request) {
        service.bind(request.getUserId(), request.getIamUsername());
        return success(true);
    }

    @DeleteMapping("/unbind")
    @Operation(summary = "管理员解除 IAM 绑定")
    @PreAuthorize("@ss.hasPermission('system:ngiam-binding:update')")
    public CommonResult<Boolean> unbind(@RequestParam("userId") Long userId) {
        service.unbind(userId);
        return success(true);
    }
}
