package io.golem.datasync.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.golem.datasync.api.ResourceNotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LabRepository {
    private final JdbcTemplate db;
    public static final Set<String> ACTIVE=Set.of("SUBMITTING","PENDING","RUNNING","SAVING","CANCELING","UNKNOWN");
    public LabRepository(JdbcTemplate db) { this.db=db; }
    private Timestamp now() { return Timestamp.from(Instant.now()); }
    public List<ObjectNode> experiments() { return db.query("SELECT document_json FROM lab_experiment WHERE archived=FALSE ORDER BY updated_at DESC", (r,n)->LabJson.obj(r.getString(1))); }
    public ObjectNode experiment(String id) {
        return db.query("SELECT document_json FROM lab_experiment WHERE id=? AND archived=FALSE",(r,n)->LabJson.obj(r.getString(1)),id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("实验不存在"));
    }
    @Transactional
    public void save(ObjectNode doc, Map<String,String> encryptedSecrets, boolean create) {
        String id=doc.path("id").asText();
        if(create) db.update("INSERT INTO lab_experiment(id,document_json,updated_at) VALUES(?,?,?)",id,doc.toString(),now());
        else db.update("UPDATE lab_experiment SET document_json=?,updated_at=? WHERE id=?",doc.toString(),now(),id);
        db.update("DELETE FROM lab_binding WHERE experiment_id=?",id);
        doc.path("bindings").fields().forEachRemaining(e->db.update("INSERT INTO lab_binding(experiment_id,node_key,data_source_id) VALUES(?,?,?)",id,e.getKey(),e.getValue().asText()));
        encryptedSecrets.forEach((name,cipher)-> {
            db.update("DELETE FROM lab_secret WHERE experiment_id=? AND secret_name=?",id,name);
            db.update("INSERT INTO lab_secret(experiment_id,secret_name,encrypted_value) VALUES(?,?,?)",id,name,cipher);
        });
    }
    public Map<String,String> secrets(String id) {
        Map<String,String> result=new LinkedHashMap<>();
        db.query("SELECT secret_name,encrypted_value FROM lab_secret WHERE experiment_id=?",r->{result.put(r.getString(1),r.getString(2));},id); return result;
    }
    @Transactional
    public void archive(String id) { db.update("UPDATE lab_experiment SET archived=TRUE WHERE id=?",id); db.update("DELETE FROM lab_binding WHERE experiment_id=?",id); }
    public boolean sourceReferenced(String id) {
        return db.queryForObject("SELECT (SELECT COUNT(*) FROM lab_binding WHERE data_source_id=?) + (SELECT COUNT(*) FROM lab_run_binding WHERE data_source_id=?)",Long.class,id,id)>0;
    }
    @Transactional
    public void insertRun(ObjectNode snapshot,String cipher) {
        String id=snapshot.path("id").asText();
        db.update("INSERT INTO lab_run(id,experiment_id,external_job_id,status,execution_cipher,snapshot_json,observation_json,created_at) VALUES(?,?,?,?,?,?,?,?)",id,snapshot.path("experimentId").asText(),snapshot.path("externalJobId").asText(),"SUBMITTING",cipher,snapshot.toString(),"{}",now());
        snapshot.path("bindings").fields().forEachRemaining(e->db.update("INSERT INTO lab_run_binding(run_id,node_key,data_source_id) VALUES(?,?,?)",id,e.getKey(),e.getValue().asText()));
        event(id,"SUBMITTING","执行快照已保存，准备提交真实 Zeta 作业");
    }
    public ObjectNode run(String id) { return runsQuery("WHERE id=?",id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("运行不存在")); }
    public List<ObjectNode> runs(String experimentId) { return runsQuery(experimentId==null ? "ORDER BY created_at DESC LIMIT 200" : "WHERE experiment_id=? ORDER BY created_at DESC LIMIT 200",experimentId==null ? new Object[0] : new Object[]{experimentId}); }
    public List<ObjectNode> active() { return runsQuery("WHERE status IN ('SUBMITTING','PENDING','RUNNING','SAVING','CANCELING','UNKNOWN')"); }
    private List<ObjectNode> runsQuery(String clause,Object...args) {
        return db.query("SELECT snapshot_json,status,observation_json FROM lab_run "+clause,(r,n)-> {
            ObjectNode v=LabJson.obj(r.getString(1));v.put("status",r.getString(2));v.set("observation",LabJson.parse(r.getString(3)));return v;
        },args);
    }
    public String cipher(String id) { return db.queryForObject("SELECT execution_cipher FROM lab_run WHERE id=?",String.class,id); }
    public void observe(String id,String status,ObjectNode observation) { db.update("UPDATE lab_run SET status=?,observation_json=? WHERE id=?",status,observation.toString(),id); }
    public void event(String id,String type,String message) { ObjectNode e=LabJson.object().put("time",Instant.now().toString()).put("type",type).put("message",message);db.update("INSERT INTO lab_run_event(run_id,event_json,created_at) VALUES(?,?,?)",id,e.toString(),now()); }
    public List<JsonNode> events(String id) { return db.query("SELECT event_json FROM lab_run_event WHERE run_id=? ORDER BY id",(r,n)->LabJson.parse(r.getString(1)),id); }
    public void sample(String id,ObjectNode sample) { db.update("INSERT INTO lab_metric_sample(run_id,sample_json,created_at) VALUES(?,?,?)",id,sample.toString(),now()); }
    public List<JsonNode> samples(String id) { return db.query("SELECT sample_json FROM lab_metric_sample WHERE run_id=? ORDER BY id",(r,n)->LabJson.parse(r.getString(1)),id); }
}
