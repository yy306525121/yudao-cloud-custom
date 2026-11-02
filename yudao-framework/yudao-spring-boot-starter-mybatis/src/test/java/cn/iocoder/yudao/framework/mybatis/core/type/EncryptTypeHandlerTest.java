package cn.iocoder.yudao.framework.mybatis.core.type;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncryptTypeHandlerTest {
    private static final String PASSWORD = "1234567890abcdef";

    @AfterEach
    void reset() throws Exception {
        var field = EncryptTypeHandler.class.getDeclaredField("encryptor");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void defaultAesReadsLegacyCiphertext() throws Exception {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("mybatis-plus.encryptor.password")).thenReturn(PASSWORD);
            String cipher = SecureUtil.aes(PASSWORD.getBytes()).encryptBase64("历史数据");
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("value")).thenReturn(cipher);
            assertEquals("历史数据", new EncryptTypeHandler().getNullableResult(rs, "value"));
            assertEquals(cipher, EncryptTypeHandler.encrypt("历史数据"));
        }
    }

    @Test
    void sm4JdbcRoundTripsAndNulls() throws Exception {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("mybatis-plus.encryptor.algorithm")).thenReturn("sM4");
            spring.when(() -> SpringUtil.getProperty("mybatis-plus.encryptor.password")).thenReturn(PASSWORD);
            EncryptTypeHandler handler = new EncryptTypeHandler();
            for (String value : new String[]{"中文", "English", ""}) {
                String cipher = EncryptTypeHandler.encrypt(value);
                assertEquals(SmUtil.sm4(PASSWORD.getBytes(StandardCharsets.UTF_8)).encryptBase64(value), cipher);
                PreparedStatement ps = mock(PreparedStatement.class);
                handler.setNonNullParameter(ps, 1, value, JdbcType.VARCHAR);
                verify(ps).setString(1, cipher);
                ResultSet rs = mock(ResultSet.class);
                CallableStatement cs = mock(CallableStatement.class);
                when(rs.getString("value")).thenReturn(cipher);
                when(rs.getString(1)).thenReturn(cipher);
                when(cs.getString(1)).thenReturn(cipher);
                assertEquals(value, handler.getNullableResult(rs, "value"));
                assertEquals(value, handler.getNullableResult(rs, 1));
                assertEquals(value, handler.getNullableResult(cs, 1));
            }
            assertNull(EncryptTypeHandler.encrypt(null));
            assertNull(handler.getNullableResult(mock(ResultSet.class), "value"));
            assertNull(handler.getNullableResult(mock(ResultSet.class), 1));
            assertNull(handler.getNullableResult(mock(CallableStatement.class), 1));
        }
    }

    @Test
    void invalidConfigurationDoesNotExposePassword() throws Exception {
        for (String[] config : new String[][]{{"DES", PASSWORD}, {"SM4", null},
                {"SM4", ""}, {"SM4", "中文密钥长度错误"}}) {
            reset();
            try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
                spring.when(() -> SpringUtil.getProperty("mybatis-plus.encryptor.algorithm")).thenReturn(config[0]);
                spring.when(() -> SpringUtil.getProperty("mybatis-plus.encryptor.password")).thenReturn(config[1]);
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> EncryptTypeHandler.encrypt("test"));
                assertTrue(error.getMessage().contains("mybatis-plus.encryptor."));
                if (config[1] != null && !config[1].isEmpty()) {
                    assertFalse(error.getMessage().contains(config[1]));
                }
            }
        }
    }
}
