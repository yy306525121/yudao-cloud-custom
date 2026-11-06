package cn.iocoder.yudao.module.infra.framework.file.core.client.aliyun;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.module.infra.framework.file.core.client.AbstractFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;

/**
 * 基于阿里云原生 SDK 的 OSS 文件客户端
 *
 * @author 芋道源码
 */
public class AliyunOssFileClient extends AbstractFileClient<AliyunOssFileClientConfig> {

    private static final Map<String, String> FILE_EXTENSION_MIME_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif",
            "bmp", "image/bmp", "webp", "image/webp", "pdf", "application/pdf");

    private OSS client;

    public AliyunOssFileClient(Long id, AliyunOssFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        OSS newClient = createClient(buildEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret());
        OSS oldClient = this.client;
        this.client = newClient;
        if (oldClient != null) {
            oldClient.shutdown();
        }
    }

    /**
     * 创建 OSS 客户端。独立为方法，便于单元测试替换 SDK 客户端。
     */
    protected OSS createClient(String endpoint, String accessKeyId, String accessKeySecret) {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    /**
     * 上传前生成最终对象路径，调用方须用同一路径上传和保存文件记录。
     */
    public static String generateUploadPath(String name) {
        String fileName = StrUtil.nullToEmpty(name).replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
        return "upload/pic/" + StrUtil.blankToDefault(baseName, "file")
                + "_" + IdUtil.fastSimpleUUID() + extension;
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        validateUpload(content, path);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        if (StrUtil.isNotEmpty(type)) {
            metadata.setContentType(type);
        }
        client.putObject(config.getBucketName(), path, new ByteArrayInputStream(content), metadata);
        return buildDomain() + StrUtil.SLASH + path;
    }

    public boolean hasUploadTypeRestriction() {
        return config.getAllowedFileExtensions() != null && !config.getAllowedFileExtensions().isEmpty();
    }

    /**
     * 只根据文件字节识别类型，不使用调用方提供的文件名或 Content-Type 推断内容。
     */
    public void validateUpload(byte[] content, String name) {
        if (!hasUploadTypeRestriction()) {
            return;
        }
        if (content == null || content.length == 0) {
            throw exception(FILE_IS_EMPTY);
        }
        String fileName = StrUtil.nullToEmpty(name).replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        int extensionIndex = fileName.lastIndexOf('.');
        String extension = extensionIndex > 0 ? fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT) : "";
        if (!config.getAllowedFileExtensions().contains(extension) || !FILE_EXTENSION_MIME_TYPES.containsKey(extension)) {
            throw exception(FILE_EXTENSION_NOT_ALLOWED, fileName, String.join(", ", config.getAllowedFileExtensions()));
        }
        String detectedType = FileTypeUtils.getMineType(content);
        if (!FILE_EXTENSION_MIME_TYPES.get(extension).equals(detectedType)) {
            throw exception(FILE_CONTENT_TYPE_MISMATCH, fileName);
        }
    }

    @Override
    public void delete(String path) {
        client.deleteObject(config.getBucketName(), path);
    }

    @Override
    public byte[] getContent(String path) throws IOException {
        try (OSSObject object = client.getObject(config.getBucketName(), path)) {
            return IoUtil.readBytes(object.getObjectContent());
        }
    }

    private String buildEndpoint() {
        // 未指定协议时交由 SDK 决定，不强制改为 HTTPS，兼容新华私有云 OSS。
        return StrUtil.trim(config.getEndpoint()).replaceAll("/+$", "");
    }

    private String buildDomain() {
        if (StrUtil.isNotEmpty(config.getDomain())) {
            return StrUtil.removeSuffix(config.getDomain(), StrUtil.SLASH);
        }
        String endpoint = buildEndpoint();
        String endpointHost = URI.create(HttpUtil.isHttp(endpoint) || HttpUtil.isHttps(endpoint)
                ? endpoint : "https://" + endpoint).getAuthority();
        return StrUtil.format("https://{}.{}", config.getBucketName(), endpointHost);
    }

}
