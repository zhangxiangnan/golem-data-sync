package io.golem.datasync.seatunnel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.golem.datasync.config.DataSyncProperties;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.service.MysqlMetadataService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeaTunnelConfigGeneratorTest {
    @TempDir Path tempDir;

    @Test
    void generatesDeterministicReplaceConfigAndRedactsCredentials() {
        CryptoService crypto = crypto();
        SeaTunnelConfigGenerator generator = new SeaTunnelConfigGenerator(
                new ObjectMapper(), crypto, new MysqlMetadataService(crypto));
        DataSourceConfigEntity source = source(crypto, "source_db", "source-secret");
        DataSourceConfigEntity target = source(crypto, "target_db", "target-secret");
        SyncJobEntity job = new SyncJobEntity();
        job.name = "orders_sync"; job.sourceTable = "orders"; job.targetTable = "orders_copy";
        job.writeMode = "REPLACE"; job.parallelism = 2; job.batchSize = 500;

        String preview = generator.pretty(generator.generate(job, source, target, true));
        assertThat(preview).contains("\"job.mode\" : \"BATCH\"")
                .contains("\"table_path\" : \"source_db.orders\"")
                .contains("\"data_save_mode\" : \"DROP_DATA\"")
                .contains("******")
                .doesNotContain("source-secret", "target-secret");

        String actual = generator.pretty(generator.generate(job, source, target, false));
        assertThat(actual).contains("source-secret", "target-secret");
    }

    @Test
    void mapsAppendMode() {
        CryptoService crypto = crypto();
        SeaTunnelConfigGenerator generator = new SeaTunnelConfigGenerator(
                new ObjectMapper(), crypto, new MysqlMetadataService(crypto));
        SyncJobEntity job = new SyncJobEntity();
        job.name = "job"; job.sourceTable = "a"; job.targetTable = "b"; job.writeMode = "APPEND";
        job.parallelism = 1; job.batchSize = 1000;
        assertThat(generator.pretty(generator.generate(job, source(crypto, "a", "x"), source(crypto, "b", "y"), true)))
                .contains("\"data_save_mode\" : \"APPEND_DATA\"");
    }

    private CryptoService crypto() {
        return new CryptoService(new DataSyncProperties(
                new DataSyncProperties.Crypto("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", tempDir.resolve("key").toString()), null));
    }

    private DataSourceConfigEntity source(CryptoService crypto, String database, String password) {
        DataSourceConfigEntity entity = new DataSourceConfigEntity();
        entity.host = "127.0.0.1"; entity.port = 3306; entity.databaseName = database;
        entity.username = "sync"; entity.encryptedPassword = crypto.encrypt(password);
        return entity;
    }
}
