package io.golem.datasync.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data-sync")
public record DataSyncProperties(Crypto crypto, SeaTunnel seatunnel) {
    public record Crypto(String masterKey, String localKeyPath) {}
    public record SeaTunnel(String baseUrl, Duration requestTimeout, Duration pollDelay) {}
}
