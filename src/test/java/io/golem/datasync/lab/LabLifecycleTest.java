package io.golem.datasync.lab;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.golem.datasync.DataSyncApplication;
import io.golem.datasync.api.*;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.service.*;
import io.golem.datasync.seatunnel.SeaTunnelClient;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes=DataSyncApplication.class)
@ActiveProfiles("test")
@Transactional
class LabLifecycleTest {
    @Autowired LabService lab;@Autowired LabRepository repo;@Autowired DataSourceService sources;@Autowired CryptoService crypto;
    @MockBean SeaTunnelClient engine;@MockBean MysqlMetadataService metadata;@MockBean LabCatalog catalog;
    String source;
    @BeforeEach void setup() throws Exception {
        reset(engine,metadata,catalog);
        var actual=new LabCatalog(".runtime/apache-seatunnel-2.3.13");
        when(catalog.group(anyString())).thenAnswer(a->actual.group(a.getArgument(0)));
        when(catalog.options(anyString())).thenAnswer(a->actual.options(a.getArgument(0)));
        when(catalog.installed(anyString())).thenReturn(true);when(catalog.savepointFilesPresent(anyString(),any())).thenReturn(true);
        Connection connection=mock(Connection.class);when(connection.isValid(5)).thenReturn(true);when(metadata.open(any())).thenReturn(connection);when(metadata.jdbcUrl(any())).thenReturn("jdbc:mysql://localhost/lab");when(metadata.listColumns(any(),any())).thenReturn(List.of(new ApiModels.ColumnInfo("id","BIGINT",java.sql.Types.BIGINT,20,0,false,true,1)));
        source=sources.create(new ApiModels.DataSourceRequest("lab-"+UUID.randomUUID(),"localhost",3306,"lab","test","test-password")).id();
        when(engine.overview()).thenReturn(LabJson.obj("{\"projectVersion\":\"2.3.13\"}"));
        when(engine.labSubmit(any(),anyString(),anyString(),anyBoolean())).thenAnswer(a->LabJson.obj("{\"jobId\":\""+a.getArgument(1)+"\"}"));
    }
    ObjectNode draft(){var d=LabJson.obj("""
       {"name":"experiment","config":{"env":{"job.mode":"BATCH","checkpoint.interval":3000},"source":[{"plugin_name":"Jdbc","plugin_output":"raw","table_path":"lab.orders"}],"transform":[],"sink":[{"plugin_name":"Jdbc","plugin_input":["raw"],"table":"orders","generate_sink_sql":true}]},"bindings":{}}
       """);d.withObject("bindings").put("source/0",source).put("sink/0",source);return d;}
    ObjectNode start(){var e=lab.save(null,draft());return lab.start(e.path("id").asText());}
    @Test void snapshotsAreImmutableEncryptedAndSingleActiveRunIsEnforced() {
        var r=start();String id=r.path("id").asText(),eid=r.path("experimentId").asText();String cipher=repo.cipher(id);
        assertThat(r.toString()).doesNotContain("test-password","execution_cipher");assertThat(cipher).startsWith("v1:").doesNotContain("test-password");
        assertThat(crypto.decrypt(cipher)).contains("test-password");
        assertThatThrownBy(()->lab.start(eid)).isInstanceOf(ConflictException.class);
        var d=lab.experiment(eid);d.withObject("config").withObject("env").put("parallelism",4);lab.save(eid,d);
        assertThat(repo.cipher(id)).isEqualTo(cipher);assertThat(repo.run(id).path("authoringConfig").path("env").has("parallelism")).isFalse();
        assertThatThrownBy(()->sources.delete(source)).isInstanceOf(ConflictException.class);
    }
    @Test void savepointRestoreUsesOriginalJobIdAndSnapshotButNewPlatformRun() {
        var r=start();String id=r.path("id").asText(),job=r.path("externalJobId").asText();
        var checkpoint=LabJson.obj("{\"pipelines\":[{\"latestSavepoint\":{\"status\":\"COMPLETED\"}}]}");
        when(engine.labJobInfo(job)).thenReturn(LabJson.obj("{\"jobId\":\""+job+"\",\"jobStatus\":\"RUNNING\"}"));
        assertThat(lab.stop(id,true).path("status").asText()).isEqualTo("SAVING");verify(engine).labStop(job,true);
        when(engine.labJobInfo(job)).thenReturn(LabJson.obj("{\"jobId\":\""+job+"\",\"jobStatus\":\"SAVEPOINT_DONE\"}"));when(engine.labCheckpoints(job)).thenReturn(checkpoint);
        lab.poll();assertThat(repo.run(id).path("status").asText()).isEqualTo("SAVED");
        var restored=lab.restore(id);assertThat(restored.path("id").asText()).isNotEqualTo(id);assertThat(restored.path("externalJobId").asText()).isEqualTo(job);assertThat(restored.path("restoredFrom").asText()).isEqualTo(id);
        assertThat(repo.cipher(restored.path("id").asText())).isEqualTo(repo.cipher(id));verify(engine).labSubmit(any(),eq(job),anyString(),eq(true));
        lab.poll();assertThat(repo.run(id).path("status").asText()).isEqualTo("SAVED");
    }
    @Test void restoreRejectsMissingCheckpointEditedConfigAndUnknownEngine() {
        var r=start();String id=r.path("id").asText(),eid=r.path("experimentId").asText(),job=r.path("externalJobId").asText();repo.observe(id,"SAVED",LabJson.object());
        when(engine.labJobInfo(job)).thenReturn(LabJson.obj("{\"jobStatus\":\"SAVEPOINT_DONE\"}"));when(engine.labCheckpoints(job)).thenReturn(LabJson.object());
        assertThatThrownBy(()->lab.restore(id)).isInstanceOf(ConflictException.class).hasMessageContaining("保存点");
        var d=lab.experiment(eid);d.withObject("config").withObject("env").put("parallelism",2);lab.save(eid,d);
        assertThatThrownBy(()->lab.restore(id)).isInstanceOf(ConflictException.class).hasMessageContaining("已修改");
        verify(engine,never()).labSubmit(any(),anyString(),anyString(),eq(true));
    }
    @Test void stopFailureAndRestartRecoveryRetainEventsWithoutResubmission() {
        var r=start();String id=r.path("id").asText(),job=r.path("externalJobId").asText();
        when(engine.labJobInfo(job)).thenReturn(LabJson.obj("{\"jobStatus\":\"RUNNING\"}"));doThrow(new RuntimeException("unavailable")).when(engine).labStop(job,false);
        assertThat(lab.stop(id,false).path("status").asText()).isEqualTo("UNKNOWN");
        lab.reconcile();String events=repo.events(id).toString();assertThat(events).contains("STOP_ERROR","RECONCILING");verify(engine,times(1)).labSubmit(any(),anyString(),anyString(),anyBoolean());
    }
    @Test void secretsStayWriteOnlyAndCopiesKeepEncryptedReferences() {
        var d=draft();d.putObject("secrets").put("extra","very-private");((ObjectNode)d.path("config").path("sink").get(0)).put("secret_access_key","${secret:extra}");
        var e=lab.save(null,d);assertThat(e.toString()).doesNotContain("very-private");var copy=lab.copy(e.path("id").asText());assertThat(copy.toString()).doesNotContain("very-private");
        assertThat(crypto.decrypt(repo.secrets(copy.path("id").asText()).get("extra"))).isEqualTo("very-private");
        ((ObjectNode)d.path("config").path("sink").get(0)).put("secret_access_key","plaintext");assertThatThrownBy(()->lab.save(null,d)).isInstanceOf(RequestValidationException.class);
    }
}
