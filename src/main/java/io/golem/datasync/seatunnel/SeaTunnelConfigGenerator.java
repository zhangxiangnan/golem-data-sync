package io.golem.datasync.seatunnel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.golem.datasync.domain.EngineType;
import io.golem.datasync.domain.WriteMode;
import io.golem.datasync.engine.GeneratedEngineConfig;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.service.MysqlMetadataService;
import org.springframework.stereotype.Component;

@Component
public class SeaTunnelConfigGenerator {
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;
    private final MysqlMetadataService metadataService;

    public SeaTunnelConfigGenerator(
            ObjectMapper objectMapper, CryptoService cryptoService, MysqlMetadataService metadataService) {
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.metadataService = metadataService;
    }

    public ObjectNode generate(
            SyncJobEntity job, DataSourceConfigEntity source, DataSourceConfigEntity target, boolean redact) {
        String sourcePassword = redact ? "******" : cryptoService.decrypt(source.encryptedPassword);
        String targetPassword = redact ? "******" : cryptoService.decrypt(target.encryptedPassword);
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode env = root.putObject("env");
        env.put("job.name", job.name);
        env.put("job.mode", "BATCH");
        env.put("parallelism", job.parallelism);

        ObjectNode sourceNode = objectMapper.createObjectNode();
        sourceNode.put("plugin_name", "Jdbc");
        sourceNode.put("plugin_output", "source_table");
        sourceNode.put("url", metadataService.jdbcUrl(source));
        sourceNode.put("driver", "com.mysql.cj.jdbc.Driver");
        sourceNode.put("user", source.username);
        sourceNode.put("password", sourcePassword);
        sourceNode.put("table_path", source.databaseName + "." + job.sourceTable);
        sourceNode.put("fetch_size", job.batchSize);
        root.putArray("source").add(sourceNode);
        root.putArray("transform");

        ObjectNode sinkNode = objectMapper.createObjectNode();
        sinkNode.put("plugin_name", "Jdbc");
        sinkNode.putArray("plugin_input").add("source_table");
        sinkNode.put("url", metadataService.jdbcUrl(target));
        sinkNode.put("driver", "com.mysql.cj.jdbc.Driver");
        sinkNode.put("user", target.username);
        sinkNode.put("password", targetPassword);
        sinkNode.put("database", target.databaseName);
        sinkNode.put("table", job.targetTable);
        sinkNode.put("generate_sink_sql", true);
        sinkNode.put("schema_save_mode", "CREATE_SCHEMA_WHEN_NOT_EXIST");
        sinkNode.put("data_save_mode",
                WriteMode.valueOf(job.writeMode) == WriteMode.APPEND ? "APPEND_DATA" : "DROP_DATA");
        sinkNode.put("batch_size", job.batchSize);
        ArrayNode sink = root.putArray("sink");
        sink.add(sinkNode);
        return root;
    }

    public String pretty(ObjectNode config) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize SeaTunnel config", exception);
        }
    }

    public GeneratedEngineConfig forEngine(
            EngineType engineType,
            SyncJobEntity job,
            DataSourceConfigEntity source,
            DataSourceConfigEntity target,
            boolean redact) {
        if (engineType == EngineType.ZETA) {
            return new GeneratedEngineConfig("json", pretty(generate(job, source, target, redact)));
        }
        if (engineType == EngineType.SPARK) {
            return new GeneratedEngineConfig("hocon", sparkHocon(job, source, target, redact));
        }
        throw new IllegalArgumentException("No config renderer for engine: " + engineType);
    }

    private String sparkHocon(
            SyncJobEntity job, DataSourceConfigEntity source, DataSourceConfigEntity target, boolean redact) {
        String sourcePassword = redact ? "******" : cryptoService.decrypt(source.encryptedPassword);
        String targetPassword = redact ? "******" : cryptoService.decrypt(target.encryptedPassword);
        String saveMode = WriteMode.valueOf(job.writeMode) == WriteMode.APPEND ? "APPEND_DATA" : "DROP_DATA";
        return """
                env {
                  "job.name" = %s
                  "job.mode" = "BATCH"
                  parallelism = %d
                }
                source {
                  Jdbc {
                    plugin_output = "source_table"
                    url = %s
                    driver = "com.mysql.cj.jdbc.Driver"
                    user = %s
                    password = %s
                    table_path = %s
                    fetch_size = %d
                  }
                }
                transform {}
                sink {
                  Jdbc {
                    plugin_input = ["source_table"]
                    url = %s
                    driver = "com.mysql.cj.jdbc.Driver"
                    user = %s
                    password = %s
                    database = %s
                    table = %s
                    generate_sink_sql = true
                    schema_save_mode = "CREATE_SCHEMA_WHEN_NOT_EXIST"
                    data_save_mode = "%s"
                    batch_size = %d
                  }
                }
                """.formatted(
                quote(job.name), job.parallelism,
                quote(metadataService.jdbcUrl(source)), quote(source.username), quote(sourcePassword),
                quote(source.databaseName + "." + job.sourceTable), job.batchSize,
                quote(metadataService.jdbcUrl(target)), quote(target.username), quote(targetPassword),
                quote(target.databaseName), quote(job.targetTable), saveMode, job.batchSize);
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to quote SeaTunnel config value", exception);
        }
    }
}
