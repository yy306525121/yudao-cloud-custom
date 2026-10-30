package cn.iocoder.yudao.framework.jasypt.bjca.core;
import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
public final class BjcaPropertyDetector implements EncryptablePropertyDetector {
    public boolean isEncrypted(String value) {
        return value != null && value.trim().startsWith("ENC(") && value.trim().endsWith(")");
    }
    public String unwrapEncryptedValue(String value) {
        String trimmed = value.trim(); return trimmed.substring(4, trimmed.length() - 1);
    }
}
