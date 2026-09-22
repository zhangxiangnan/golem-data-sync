package io.golem.datasync.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretSanitizerTest {
    private final SecretSanitizer sanitizer = new SecretSanitizer();

    @Test
    void removesKnownSecretsAndPasswordParameters() {
        String message = "connect failed password=visible-secret; jdbc pwd=query-secret token=opaque";

        assertThat(sanitizer.sanitize(message, List.of("opaque")))
                .isEqualTo("connect failed password=******; jdbc pwd=****** token=******");
    }

    @Test
    void limitsErrorLengthAndHandlesEmptyMessage() {
        assertThat(sanitizer.sanitize(null, List.of())).isEqualTo("Unknown error");
        assertThat(sanitizer.sanitize("x".repeat(1200), List.of())).hasSize(1000);
    }
}
