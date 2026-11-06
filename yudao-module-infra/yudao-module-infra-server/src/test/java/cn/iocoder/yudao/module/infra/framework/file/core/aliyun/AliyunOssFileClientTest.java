package cn.iocoder.yudao.module.infra.framework.file.core.aliyun;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.framework.file.core.client.aliyun.AliyunOssFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.aliyun.AliyunOssFileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.enums.FileStorageEnum;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AliyunOssFileClientTest {

    @Test
    public void testUpload_defaultDomain() {
        // 准备参数
        OSS oss = mock(OSS.class);
        TestAliyunOssFileClient client = new TestAliyunOssFileClient(buildConfig(), oss);
        client.init();
        byte[] content = "hello oss".getBytes();

        // 调用
        String url = client.upload(content, "20260724/demo.jpg", "image/jpeg");

        // 断言
        assertEquals("https://demo-bucket.oss-cn-hangzhou.aliyuncs.com/20260724/demo.jpg", url);
        assertEquals("oss-cn-hangzhou.aliyuncs.com", client.getLastEndpoint());
        var inputCaptor = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        var metadataCaptor = org.mockito.ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(oss).putObject(eq("demo-bucket"), eq("20260724/demo.jpg"), inputCaptor.capture(),
                metadataCaptor.capture());
        assertArrayEquals(content, IoUtil.readBytes(inputCaptor.getValue()));
        assertEquals("image/jpeg", metadataCaptor.getValue().getContentType());
        assertEquals(content.length, metadataCaptor.getValue().getContentLength());
    }

    @Test
    public void testUpload_customDomainAndEndpointWithProtocol() {
        // 准备参数
        OSS oss = mock(OSS.class);
        AliyunOssFileClientConfig config = buildConfig()
                .setEndpoint("http://oss-cn-shanghai.aliyuncs.com/")
                .setDomain("https://cdn.example.com/");
        TestAliyunOssFileClient client = new TestAliyunOssFileClient(config, oss);
        client.init();

        // 调用
        String url = client.upload(new byte[]{1}, "demo.png", "image/png");

        // 断言
        assertEquals("https://cdn.example.com/demo.png", url);
        assertEquals("http://oss-cn-shanghai.aliyuncs.com", client.getLastEndpoint());
    }

    @Test
    public void testGetContentAndDelete() throws Exception {
        // 准备参数
        byte[] content = "content".getBytes();
        OSS oss = mock(OSS.class);
        OSSObject object = spy(new OSSObject());
        object.setObjectContent(new ByteArrayInputStream(content));
        when(oss.getObject("demo-bucket", "demo.txt")).thenReturn(object);
        TestAliyunOssFileClient client = new TestAliyunOssFileClient(buildConfig(), oss);
        client.init();

        // 调用和断言
        assertArrayEquals(content, client.getContent("demo.txt"));
        verify(object).close();
        client.delete("demo.txt");
        verify(oss).deleteObject("demo-bucket", "demo.txt");
    }

    @Test
    public void testRefresh_shutdownOldClient() {
        // 准备参数
        OSS oldClient = mock(OSS.class);
        OSS newClient = mock(OSS.class);
        TestAliyunOssFileClient client = new TestAliyunOssFileClient(buildConfig(), oldClient, newClient);
        client.init();

        // 调用
        client.refresh(buildConfig().setBucketName("new-bucket"));

        // 断言
        verify(oldClient).shutdown();
        verify(newClient, never()).shutdown();
    }

    @Test
    public void testConfigValidationAndStorageMapping() {
        assertEquals(AliyunOssFileClientConfig.class, FileStorageEnum.ALIYUN_OSS.getConfigClass());
        assertEquals(AliyunOssFileClient.class, FileStorageEnum.ALIYUN_OSS.getClientClass());
        assertEquals(21, FileStorageEnum.ALIYUN_OSS.getStorage());

        AliyunOssFileClientConfig config = buildConfig().setBucketName(" ");
        assertThrows(ConstraintViolationException.class, () -> ValidationUtils.validate(
                Validation.buildDefaultValidatorFactory().getValidator(), config));
    }

    @Test
    public void testConfigJsonTypeHandler() {
        FileConfigDO.FileClientConfigTypeHandler handler =
                new FileConfigDO.FileClientConfigTypeHandler(AliyunOssFileClientConfig.class);

        String json = handler.toJson(buildConfig());
        Object result = handler.parse(json);

        assertInstanceOf(AliyunOssFileClientConfig.class, result);
        assertEquals(buildConfig(), result);
    }

    @ParameterizedTest
    @CsvSource({
            "oss-cn-hangzhou.aliyuncs.com, http://oss-cn-hangzhou.aliyuncs.com",
            "http://oss-cn-hangzhou.aliyuncs.com/, http://oss-cn-hangzhou.aliyuncs.com",
            "https://oss-cn-hangzhou.aliyuncs.com/, https://oss-cn-hangzhou.aliyuncs.com",
            "'  oss-cn-hangzhou.aliyuncs.com///  ', http://oss-cn-hangzhou.aliyuncs.com"
    })
    public void testInit_actualSdkProtocol(String endpoint, String expectedEndpoint) {
        // 使用生产初始化方法和真实 SDK，仅构造客户端，不发起网络请求。
        AliyunOssFileClient client = new AliyunOssFileClient(21L, buildConfig().setEndpoint(endpoint));
        client.init();
        OSSClient oss = (OSSClient) ReflectionTestUtils.getField(client, "client");
        assertNotNull(oss);
        try {
            assertEquals(expectedEndpoint, oss.getEndpoint().toString());
        } finally {
            oss.shutdown();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "images.pic, images, .pic",
            "folder/images.pic, images, .pic",
            "C:\\fakepath\\images.pic, images, .pic",
            "archive.tar.gz, archive.tar, .gz",
            "图片.PNG, 图片, .PNG",
            "README, README, ''"
    })
    public void testGenerateUploadPath(String name, String baseName, String extension) {
        String path = AliyunOssFileClient.generateUploadPath(name);
        String prefix = "upload/pic/" + baseName + "_";
        assertTrue(path.startsWith(prefix));
        assertTrue(path.endsWith(extension));
        String randomPart = path.substring(prefix.length(), path.length() - extension.length());
        assertTrue(randomPart.matches("[0-9a-f]{32}"));
        assertNotEquals(path, AliyunOssFileClient.generateUploadPath(name));
    }

    @Test
    public void testUpload_failurePropagated() {
        OSS oss = mock(OSS.class);
        TestAliyunOssFileClient client = new TestAliyunOssFileClient(buildConfig(), oss);
        client.init();
        RuntimeException failure = new RuntimeException("upload failed");
        when(oss.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> client.upload(new byte[]{1}, "upload/pic/demo.jpg", "image/jpeg")));
    }

    private static AliyunOssFileClientConfig buildConfig() {
        return new AliyunOssFileClientConfig()
                .setEndpoint("oss-cn-hangzhou.aliyuncs.com")
                .setAccessKeyId("access-key-id")
                .setAccessKeySecret("access-key-secret")
                .setBucketName("demo-bucket")
                .setDomain(null);
    }

    private static class TestAliyunOssFileClient extends AliyunOssFileClient {

        private final Deque<OSS> clients = new ArrayDeque<>();
        private String lastEndpoint;

        TestAliyunOssFileClient(AliyunOssFileClientConfig config, OSS... clients) {
            super(21L, config);
            this.clients.addAll(Arrays.asList(clients));
        }

        @Override
        protected OSS createClient(String endpoint, String accessKeyId, String accessKeySecret) {
            this.lastEndpoint = endpoint;
            return clients.removeFirst();
        }

        String getLastEndpoint() {
            return lastEndpoint;
        }

    }

}
