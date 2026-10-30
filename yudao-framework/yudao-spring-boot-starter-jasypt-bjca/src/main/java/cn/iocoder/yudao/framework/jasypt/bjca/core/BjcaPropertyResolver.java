package cn.iocoder.yudao.framework.jasypt.bjca.core;
import cn.iocoder.yudao.framework.jasypt.bjca.config.MscpProperties;
import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyResolver;
import com.ulisesbocchio.jasyptspringboot.exception.DecryptionException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

public final class BjcaPropertyResolver implements EncryptablePropertyResolver {
    private final EncryptablePropertyDetector detector;
    private final ConfigurableEnvironment environment;
    private final ThreadLocal<Boolean> resolving = ThreadLocal.withInitial(() -> false);
    public BjcaPropertyResolver(EncryptablePropertyDetector detector, ConfigurableEnvironment environment) {
        this.detector = detector; this.environment = environment;
    }
    public String resolvePropertyValue(String value) {
        if (!detector.isEncrypted(value)) return value;
        if (resolving.get()) throw new DecryptionException("BJCA connection settings must not contain ENC values");
        resolving.set(true);
        try {
            // Bind on demand rather than requesting a bean during property-source post-processing.
            MscpProperties properties = Binder.get(environment).bind("jasypt.mscp", MscpProperties.class)
                    .orElseGet(MscpProperties::new);
            properties.validateConnection();
            String key = properties.getMainKeyId();
            return new BjcaClient(properties).decrypt(detector.unwrapEncryptedValue(value), key);
        } catch (Exception e) {
            throw new DecryptionException("BJCA configuration decryption failed; check connection settings and key");
        } finally { resolving.remove(); }
    }
}
