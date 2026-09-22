package io.golem.datasync.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.golem.datasync.config.DataSyncProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CryptoServiceTest {
    @TempDir Path tempDir;

    @Test
    void encryptsWithRandomIvAndDecrypts() {
        CryptoService service = new CryptoService(new DataSyncProperties(
                new DataSyncProperties.Crypto("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", tempDir.resolve("key").toString()),
                null));
        String first = service.encrypt("secret");
        String second = service.encrypt("secret");
        assertThat(first).isNotEqualTo(second).doesNotContain("secret");
        assertThat(service.decrypt(first)).isEqualTo("secret");
        assertThat(service.decrypt(second)).isEqualTo("secret");
    }

    @Test
    void rejectsInvalidKeyLength() {
        assertThatThrownBy(() -> new CryptoService(new DataSyncProperties(
                new DataSyncProperties.Crypto("YQ==", tempDir.resolve("key").toString()), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
