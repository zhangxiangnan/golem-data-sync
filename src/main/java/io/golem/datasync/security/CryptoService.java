package io.golem.datasync.security;

import io.golem.datasync.config.DataSyncProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CryptoService {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(DataSyncProperties properties) {
        this.key = loadKey(properties.crypto());
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt credential", exception);
        }
    }

    public String decrypt(String value) {
        try {
            String[] parts = value.split(":", 3);
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted credential format");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt credential", exception);
        }
    }

    private SecretKey loadKey(DataSyncProperties.Crypto config) {
        if (config != null && StringUtils.hasText(config.masterKey())) {
            byte[] bytes = Base64.getDecoder().decode(config.masterKey());
            validateLength(bytes);
            return new SecretKeySpec(bytes, "AES");
        }
        Path path = Path.of(config == null || !StringUtils.hasText(config.localKeyPath())
                ? ".runtime/master.key" : config.localKeyPath());
        try {
            if (Files.exists(path)) {
                byte[] bytes = Base64.getDecoder().decode(Files.readString(path).trim());
                validateLength(bytes);
                return new SecretKeySpec(bytes, "AES");
            }
            Files.createDirectories(path.toAbsolutePath().getParent());
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey generated = generator.generateKey();
            Files.writeString(path, Base64.getEncoder().encodeToString(generated.getEncoded()), StandardCharsets.UTF_8);
            return generated;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize local credential key", exception);
        }
    }

    private void validateLength(byte[] keyBytes) {
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("DATA_SYNC_MASTER_KEY must be a base64 encoded 32-byte key");
        }
    }
}
