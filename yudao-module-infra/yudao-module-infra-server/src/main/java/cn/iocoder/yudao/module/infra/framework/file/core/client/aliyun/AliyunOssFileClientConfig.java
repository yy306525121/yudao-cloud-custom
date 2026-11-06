package cn.iocoder.yudao.module.infra.framework.file.core.client.aliyun;

import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClientConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.URL;

import java.util.List;
import java.util.Locale;

/**
 * 阿里云 OSS 文件客户端的配置类
 *
 * @author 芋道源码
 */
@Data
public class AliyunOssFileClientConfig implements FileClientConfig {

    /**
     * OSS 节点地址，例如：oss-cn-hangzhou.aliyuncs.com。
     * 不带协议时遵循 OSS SDK 默认协议；需要 HTTPS 时显式填写 https://。
     */
    @NotBlank(message = "endpoint 不能为空")
    private String endpoint;

    /**
     * AccessKey ID
     */
    @NotBlank(message = "accessKeyId 不能为空")
    @ToString.Exclude
    private String accessKeyId;

    /**
     * AccessKey Secret
     */
    @NotBlank(message = "accessKeySecret 不能为空")
    @ToString.Exclude
    private String accessKeySecret;

    /**
     * Bucket 名称
     */
    @NotBlank(message = "bucketName 不能为空")
    private String bucketName;

    /**
     * 自定义域名，为空时使用 https://{bucketName}.{endpoint}
     */
    @URL(message = "domain 必须是 URL 格式")
    private String domain;

    /**
     * 允许上传的扩展名。未配置或为空时不限制；配置后同时校验扩展名和实际内容类型。
     */
    private List<@NotNull(message = "允许上传类型不能为空项")
            @Pattern(regexp = "jpg|jpeg|png|gif|bmp|webp|pdf",
                    message = "允许上传类型仅支持 jpg、jpeg、png、gif、bmp、webp、pdf") String> allowedFileExtensions;

    public AliyunOssFileClientConfig setAllowedFileExtensions(List<String> allowedFileExtensions) {
        this.allowedFileExtensions = allowedFileExtensions == null ? null : allowedFileExtensions.stream()
                .map(extension -> extension == null ? null : extension.trim().toLowerCase(Locale.ROOT))
                .distinct().toList();
        return this;
    }

}
