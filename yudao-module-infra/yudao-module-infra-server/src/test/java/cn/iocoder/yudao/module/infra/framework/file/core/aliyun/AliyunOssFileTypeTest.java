package cn.iocoder.yudao.module.infra.framework.file.core.aliyun;

import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.framework.file.core.client.aliyun.AliyunOssFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.aliyun.AliyunOssFileClientConfig;
import com.aliyun.oss.OSS;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AliyunOssFileTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf"})
    void allowedContentUploadsWithUppercaseExtension(String extension) throws Exception {
        OSS oss = mock(OSS.class);
        AliyunOssFileClient client = client(oss, List.of(extension));
        String path = "upload/pic/sample_123." + extension.toUpperCase(Locale.ROOT);
        byte[] bytes = sample(extension);
        assertEquals("https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/" + path,
                client.upload(bytes, path, "application/octet-stream"));
        verify(oss).putObject(eq("demo-bucket"), eq(path), any(InputStream.class), any());
    }

    @Test
    void renamedContentAndForgedMimeAreRejected() throws Exception {
        OSS oss = mock(OSS.class);
        AliyunOssFileClient client = client(oss, List.of("jpg", "pdf"));
        assertServiceException(() -> client.upload(sample("pdf"), "fake.jpg", "image/jpeg"),
                FILE_CONTENT_TYPE_MISMATCH, "fake.jpg");
        assertServiceException(() -> client.upload("arbitrary text".getBytes(StandardCharsets.UTF_8),
                "unknown.jpg", "image/jpeg"), FILE_CONTENT_TYPE_MISMATCH, "unknown.jpg");
        assertServiceException(() -> client.upload(sample("png"), "fake.pdf", "application/pdf"),
                FILE_CONTENT_TYPE_MISMATCH, "fake.pdf");
        verifyNoInteractions(oss);
    }

    @Test
    void extensionAndEmptyContentAreRejected() throws Exception {
        OSS oss = mock(OSS.class);
        AliyunOssFileClient client = client(oss, List.of("jpg"));
        byte[] jpeg = sample("jpg");
        for (String name : List.of("photo.jpeg", "photo.jpg.exe", "photo", ".jpg")) {
            assertServiceException(() -> client.upload(jpeg, name, "image/jpeg"),
                    FILE_EXTENSION_NOT_ALLOWED, name, "jpg");
        }
        assertServiceException(() -> client.upload(new byte[0], "photo.jpg", "image/jpeg"), FILE_IS_EMPTY);
        assertServiceException(() -> client.upload(null, "photo.jpg", "image/jpeg"), FILE_IS_EMPTY);
        verifyNoInteractions(oss);
    }

    @Test
    void absentAndEmptyAllowlistPreserveLegacyBehavior() {
        for (List<String> extensions : Arrays.asList(null, List.<String>of())) {
            OSS oss = mock(OSS.class);
            AliyunOssFileClient client = client(oss, extensions);
            assertFalse(client.hasUploadTypeRestriction());
            assertDoesNotThrow(() -> client.upload(new byte[0], "sample.pic", "arbitrary/type"));
            verify(oss).putObject(eq("demo-bucket"), eq("sample.pic"), any(InputStream.class), any());
        }
    }

    @Test
    void configNormalizesAndRoundTripsWhileOldJsonRemainsUnrestricted() {
        AliyunOssFileClientConfig config = config(List.of(" JPG ", "PNG", "jpg"));
        assertEquals(List.of("jpg", "png"), config.getAllowedFileExtensions());
        FileConfigDO.FileClientConfigTypeHandler handler = new FileConfigDO.FileClientConfigTypeHandler(AliyunOssFileClientConfig.class);
        assertEquals(config, handler.parse(handler.toJson(config)));
        String oldJson = handler.toJson(config(null)).replace(",\"allowedFileExtensions\":null", "")
                .replace("\"allowedFileExtensions\":null,", "");
        assertNull(((AliyunOssFileClientConfig) handler.parse(oldJson)).getAllowedFileExtensions());
    }

    @Test
    void invalidConfigOptionsAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (List<String> values : List.of(List.of("exe"), List.of(".jpg"), List.of(""), Arrays.asList("jpg", null))) {
                assertThrows(ConstraintViolationException.class,
                        () -> ValidationUtils.validate(factory.getValidator(), config(values)));
            }
            assertDoesNotThrow(() -> ValidationUtils.validate(factory.getValidator(), config(List.of(" JPG ", "jpg"))));
        }
    }

    @Test
    void clientRefreshChangesAndClearsRestriction() throws Exception {
        OSS oss = mock(OSS.class);
        AliyunOssFileClient client = client(oss, List.of("jpg"));
        assertServiceException(() -> client.upload(sample("png"), "sample.png", "image/png"),
                FILE_EXTENSION_NOT_ALLOWED, "sample.png", "jpg");
        client.refresh(config(List.of("png")));
        assertDoesNotThrow(() -> client.upload(sample("png"), "sample.png", "image/png"));
        client.refresh(config(List.of()));
        assertDoesNotThrow(() -> client.upload(new byte[]{1}, "sample.pic", "image/jpeg"));
    }

    public static AliyunOssFileClientConfig config(List<String> extensions) {
        return new AliyunOssFileClientConfig().setEndpoint("oss-cn-hangzhou.aliyuncs.com")
                .setAccessKeyId("test-id").setAccessKeySecret("test-secret").setBucketName("demo-bucket")
                .setAllowedFileExtensions(extensions);
    }

    public static AliyunOssFileClient client(OSS oss, List<String> extensions) {
        AliyunOssFileClient client = new AliyunOssFileClient(21L, config(extensions)) {
            @Override
            protected OSS createClient(String endpoint, String accessKeyId, String accessKeySecret) {
                return oss;
            }
        };
        client.init();
        return client;
    }

    public static byte[] sample(String extension) throws Exception {
        if ("webp".equals(extension)) {
            // 完整的 1x1 WebP 图片。
            return Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        }
        if ("pdf".equals(extension)) {
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            pdf.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            int[] offsets = new int[4];
            String[] objects = {"<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>"};
            for (int i = 1; i <= 3; i++) {
                offsets[i] = pdf.size();
                pdf.write((i + " 0 obj\n" + objects[i - 1] + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
            }
            int xref = pdf.size();
            pdf.write("xref\n0 4\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
            for (int i = 1; i <= 3; i++) {
                pdf.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.US_ASCII));
            }
            pdf.write(("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
            return pdf.toByteArray();
        }
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), extension, image));
        return image.toByteArray();
    }
}
