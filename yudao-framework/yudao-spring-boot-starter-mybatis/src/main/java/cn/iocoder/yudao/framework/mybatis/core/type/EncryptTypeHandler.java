package cn.iocoder.yudao.framework.mybatis.core.type;

import cn.hutool.core.lang.Assert;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字段加密 TypeHandler，支持 AES（默认）和 SM4，密文采用 Base64。
 * 通过 mybatis-plus.encryptor.algorithm 选择算法，mybatis-plus.encryptor.password 设置密钥。
 * 配置修改后需要重启；切换算法前须迁移旧密文，不支持混读。
 *
 * @author 芋道源码
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";

    private static final String ALGORITHM_PROPERTY_NAME = "mybatis-plus.encryptor.algorithm";

    private static SymmetricCrypto encryptor;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decrypt(value);
    }

    private static String decrypt(String value) {
        if (value == null) {
            return null;
        }
        return getEncryptor().decryptStr(value);
    }

    public static String encrypt(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return getEncryptor().encryptBase64(rawValue);
    }

    private static synchronized SymmetricCrypto getEncryptor() {
        if (encryptor != null) {
            return encryptor;
        }
        String algorithm = SpringUtil.getProperty(ALGORITHM_PROPERTY_NAME);
        if (algorithm == null) {
            algorithm = "AES";
        }
        Assert.isTrue("AES".equalsIgnoreCase(algorithm) || "SM4".equalsIgnoreCase(algorithm),
                "配置项({}) 仅支持 AES、SM4", ALGORITHM_PROPERTY_NAME);
        String password = SpringUtil.getProperty(ENCRYPTOR_PROPERTY_NAME);
        Assert.notEmpty(password, "配置项({}) 不能为空", ENCRYPTOR_PROPERTY_NAME);
        if ("SM4".equalsIgnoreCase(algorithm)) {
            byte[] key = password.getBytes(StandardCharsets.UTF_8);
            Assert.isTrue(key.length == 16, "配置项({}) 的 SM4 密钥 UTF-8 编码后必须为 16 字节",
                    ENCRYPTOR_PROPERTY_NAME);
            encryptor = SmUtil.sm4(key); // SM4/ECB/PKCS5Padding
        } else {
            // 保留旧 AES 密钥编码方式，兼容已有密文。
            encryptor = SecureUtil.aes(password.getBytes());
        }
        return encryptor;
    }

}
