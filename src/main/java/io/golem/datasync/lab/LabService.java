package io.golem.datasync.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.golem.datasync.api.*;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.seatunnel.SeaTunnelClient;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class LabService {
    private final LabRepository repo;
    private final LabConfigService configs;
    private final LabCatalog catalog;
    private final CryptoService crypto;
    private final SeaTunnelClient engine;
    public LabService(LabRepository repo,LabConfigService configs,LabCatalog catalog,CryptoService crypto,SeaTunnelClient engine) {this.repo=repo;this.configs=configs;this.catalog=catalog;this.crypto=crypto;this.engine=engine;}
    public ObjectNode catalogDocument() {
        ObjectNode document=catalog.document();List<ObjectNode> runs=repo.runs(null);
        for(JsonNode item:document.path("groups")) {
            ObjectNode group=(ObjectNode)item;var evidence=group.putArray("verifiedRunIds");String key=group.path("id").asText();
            for(ObjectNode run:runs) {
                if(!run.path("status").asText().equals("SUCCEEDED")||!run.path("engineVersion").asText().equals("2.3.13"))continue;
                boolean match=false;for(String section:List.of("source","transform","sink"))for(JsonNode node:run.path("authoringConfig").path(section))if(key.equals(section+"."+node.path("plugin_name").asText()))match=true;
                if(match&&evidence.size()<5)evidence.add(run.path("id").asText());
            }
            group.put("platformVerified",!evidence.isEmpty());
            if(!evidence.isEmpty())group.put("verificationNote","有本平台真实成功运行记录；仅验证记录中的配置组合，不代表所有参数组合兼容");
        }return document;
    }
    public List<ObjectNode> experiments(){return repo.experiments();}
    public ObjectNode experiment(String id){return repo.experiment(id);}
    private Map<String,String> secrets(String id) {var result=new LinkedHashMap<String,String>();if(id!=null)repo.secrets(id).forEach((k,v)->result.put(k,crypto.decrypt(v)));return result;}
    public synchronized ObjectNode save(String id,JsonNode body) {
        boolean create=id==null;if(create)id=UUID.randomUUID().toString();else repo.experiment(id);
        String name=body.path("name").asText().trim();if(name.isEmpty()||name.length()>160)throw new RequestValidationException("实验名称应为 1–160 个字符");
        Map<String,String> all=secrets(create?null:id), encrypted=new LinkedHashMap<>();
        JsonNode incoming=body.path("secrets");if(!incoming.isMissingNode()&&!incoming.isObject())throw new RequestValidationException("secrets 必须是对象");
        incoming.fields().forEachRemaining(e->{if(!e.getKey().matches("[A-Za-z0-9_-]{1,120}")||!e.getValue().isTextual()||e.getValue().asText().isBlank())throw new RequestValidationException("秘密名称或值不合法");all.put(e.getKey(),e.getValue().asText());encrypted.put(e.getKey(),crypto.encrypt(e.getValue().asText()));});
        JsonNode bindings=body.path("bindings");
        var prepared=configs.prepare(body.path("config"),bindings,all,false);
        // Invalid plugin drafts are useful, but plaintext secrets must never enter persistence or exports.
        var secretErrors=LabJson.JSON.createArrayNode();configs.checkSecrets(prepared.authoring(),"config",secretErrors);
        if(!secretErrors.isEmpty())throw new RequestValidationException(secretErrors.toString());
        var doc=LabJson.object().put("id",id).put("name",name).put("description",body.path("description").asText("")).put("updatedAt",Instant.now().toString());
        doc.set("config",prepared.authoring());doc.set("bindings",bindings);doc.set("secretNames",LabJson.JSON.valueToTree(all.keySet()));
        repo.save(doc,encrypted,create);return doc;
    }
    public synchronized ObjectNode copy(String id) {
        ObjectNode old=repo.experiment(id);old.put("name",old.path("name").asText()+" 副本");old.set("secrets",LabJson.JSON.valueToTree(secrets(id)));return save(null,old);
    }
    public synchronized void delete(String id){repo.experiment(id);ensureIdle(id);repo.archive(id);}
    public ObjectNode validate(String id,boolean preflight){var d=repo.experiment(id);return configs.prepare(d.path("config"),d.path("bindings"),secrets(id),preflight).report();}
    private void ensureIdle(String id){if(repo.active().stream().anyMatch(r->r.path("experimentId").asText().equals(id)))throw new ConflictException("此实验已有活动运行；状态未知时请先确认引擎状态");}
    private ObjectNode version() {
        try {var info=engine.overview();if(info==null||!info.path("projectVersion").asText().equals("2.3.13"))throw new ConflictException("实验仅允许实际 SeaTunnel 2.3.13 + Zeta 引擎");return (ObjectNode)info;}
        catch(ConflictException e){throw e;}catch(Exception e){throw new ConflictException("无法确认本机 Zeta 版本和状态，请检查引擎连接");}
    }
    public synchronized ObjectNode start(String id) {
        ObjectNode doc=repo.experiment(id);ensureIdle(id);ObjectNode engineInfo=version();
        var p=configs.prepare(doc.path("config"),doc.path("bindings"),secrets(id),true);
        if(!p.report().path("valid").asBoolean())throw new RequestValidationException(p.report().path("errors").toString());
        String runId=UUID.randomUUID().toString(), external=Long.toString((UUID.randomUUID().getMostSignificantBits()&Long.MAX_VALUE));
        ObjectNode snapshot=LabJson.object().put("id",runId).put("experimentId",id).put("experimentName",doc.path("name").asText()).put("externalJobId",external).put("createdAt",Instant.now().toString()).put("engineVersion","2.3.13").put("engineType","ZETA");
        snapshot.set("config",p.report().path("preview"));snapshot.set("authoringConfig",p.authoring());snapshot.set("bindings",doc.path("bindings"));snapshot.set("validation",p.report());snapshot.set("engineAtSubmit",LabJson.redact(engineInfo,p.secrets()));
        snapshot.put("configDigest",LabJson.digest(p.execution()));snapshot.put("draftDigest",draftDigest(doc));
        repo.insertRun(snapshot,crypto.encrypt(p.execution().toString()));
        submit(snapshot,p.execution(),false,p.secrets());return detail(runId);
    }
    private String draftDigest(JsonNode doc){var o=LabJson.object();o.set("config",doc.path("config"));o.set("bindings",doc.path("bindings"));if(!repo.secrets(doc.path("id").asText()).isEmpty())o.set("secretRevisions",LabJson.JSON.valueToTree(repo.secrets(doc.path("id").asText())));return LabJson.digest(o);}
    private void submit(ObjectNode snapshot,ObjectNode execution,boolean restore,List<String> values) {
        String id=snapshot.path("id").asText();
        try {
            JsonNode response=engine.labSubmit(execution,snapshot.path("externalJobId").asText(),execution.path("env").path("job.name").asText(snapshot.path("experimentName").asText()),restore);
            JsonNode item=response!=null&&response.isArray()?response.path(0):response;
            if(item==null||!snapshot.path("externalJobId").asText().equals(item.path("jobId").asText()))throw new IllegalStateException("引擎未确认预分配的 jobId，需重新确认状态");
            repo.observe(id,"PENDING",LabJson.object());repo.event(id,"SUBMITTED",restore?"使用原外部 jobId 和不可变执行快照恢复；未进行全量降级":"引擎已接受提交");
        }catch(WebClientResponseException e) {
            // A definite client rejection is terminal. Timeout/server failure may still have submitted the job.
            String status=e.getStatusCode().is4xxClientError()||definiteRejection(e.getResponseBodyAsString())?"FAILED":"UNKNOWN";
            ObjectNode obs=LabJson.object().put("lastError","引擎提交未成功确认 (HTTP "+e.getStatusCode().value()+")："+LabJson.scrub(e.getResponseBodyAsString(),values));if(status.equals("FAILED"))obs.put("finishedAt",Instant.now().toString());
            repo.observe(id,status,obs);repo.event(id,"SUBMIT_ERROR",obs.path("lastError").asText());
        }catch(Exception e){repo.observe(id,"UNKNOWN",LabJson.object().put("lastError",LabJson.scrub(e.getMessage(),values)));repo.event(id,"SUBMIT_UNCONFIRMED","提交结果未确认，保留预分配 jobId 并继续核对；禁止重复提交");}
    }
    public List<ObjectNode> runs(String id){return repo.runs(id);}
    public ObjectNode detail(String id){ObjectNode r=repo.run(id);r.set("events",LabJson.JSON.valueToTree(repo.events(id)));return r;}
    public List<JsonNode> metrics(String id){repo.run(id);return repo.samples(id);}
    public synchronized ObjectNode stop(String id,boolean savepoint) {
        ObjectNode r=repo.run(id);String status=r.path("status").asText();
        if(!LabRepository.ACTIVE.contains(status)||status.equals("SAVING")||status.equals("CANCELING"))throw new ConflictException("当前状态不能再次停止");
        if(savepoint&&!r.path("authoringConfig").path("env").has("checkpoint.interval"))throw new ConflictException("批量保存点实验必须显式配置 checkpoint.interval 后新运行");
        ObjectNode info;
        try{info=(ObjectNode)engine.labJobInfo(r.path("externalJobId").asText());}catch(Exception e){throw new ConflictException("引擎状态不可确认，停止请求未发送");}
        if(info==null||!Set.of("RUNNING","PENDING","CREATED","SCHEDULED").contains(info.path("jobStatus").asText()))throw new ConflictException("引擎任务未处于可停止状态");
        ObjectNode obs=(ObjectNode)r.path("observation").deepCopy();obs.put("stopRequestedAt",Instant.now().toString());
        String next=savepoint?"SAVING":"CANCELING";repo.observe(id,next,obs);repo.event(id,next,savepoint?"已请求创建保存点并停止；不表示数据全部同步成功":"已请求取消，不创建保存点");
        try{engine.labStop(r.path("externalJobId").asText(),savepoint);}catch(Exception e){repo.event(id,"STOP_ERROR","停止请求失败或结果未确认；将继续查询引擎，不假定已停止");obs.put("lastError","停止请求结果未确认");repo.observe(id,"UNKNOWN",obs);}
        return detail(id);
    }
    public synchronized ObjectNode restore(String id) {
        ObjectNode original=repo.run(id);String experimentId=original.path("experimentId").asText();ensureIdle(experimentId);version();
        if(!original.path("status").asText().equals("SAVED"))throw new ConflictException("仅允许从成功保存点暂停的运行恢复");
        ObjectNode doc=repo.experiment(experimentId);
        if(!draftDigest(doc).equals(original.path("draftDigest").asText()))throw new ConflictException("实验配置或绑定已修改，不能恢复；请还原原配置或创建新运行");
        // An external ID can have multiple platform runs, but only its latest saved run is restorable.
        if(repo.runs(experimentId).stream().anyMatch(r->r.path("restoredFrom").asText().equals(id)))throw new ConflictException("此保存点已用于恢复，请选择最新暂停运行");
        JsonNode current, checkpoints;
        try{current=engine.labJobInfo(original.path("externalJobId").asText());checkpoints=engine.labCheckpoints(original.path("externalJobId").asText());}
        catch(Exception e){throw new ConflictException("引擎或保存点状态不可确认；不会退化为全量重跑");}
        if(current==null||!current.path("jobStatus").asText().equals("SAVEPOINT_DONE"))throw new ConflictException("原引擎任务未确认 SAVEPOINT_DONE，不能恢复");
        if(!hasSavepoint(checkpoints))throw new ConflictException("成功保存点缺失或引擎未返回保存点信息；不会全量重跑");
        if(!catalog.savepointFilesPresent(original.path("externalJobId").asText(),checkpoints))throw new ConflictException("本机成功保存点文件缺失或存储位置不可确认；不会全量重跑");
        ObjectNode execution=LabJson.obj(crypto.decrypt(repo.cipher(id)));
        ObjectNode snapshot=original.deepCopy();snapshot.remove(List.of("status","observation","events"));
        String newId=UUID.randomUUID().toString();snapshot.put("id",newId).put("createdAt",Instant.now().toString()).put("restoredFrom",id);
        repo.insertRun(snapshot,repo.cipher(id));repo.event(newId,"RESTORE", "关联原运行 "+id+"，复用外部 jobId "+original.path("externalJobId").asText());
        submit(snapshot,execution,true,executionSecrets(snapshot,execution));return detail(newId);
    }
    static boolean definiteRejection(String body) {
        try{return "fail".equals(LabJson.parse(body).path("status").asText());}catch(Exception ignored){return false;}
    }
    private List<String> executionSecrets(JsonNode run,JsonNode execution) {
        List<String> result=new ArrayList<>(LabJson.secretValues(execution));
        collectReferences(run.path("authoringConfig"),execution,result);return result;
    }
    private void collectReferences(JsonNode author,JsonNode exec,List<String> values) {
        if(author.isObject())author.fields().forEachRemaining(e->collectReferences(e.getValue(),exec.path(e.getKey()),values));
        else if(author.isArray())for(int i=0;i<author.size();i++)collectReferences(author.path(i),exec.path(i),values);
        else if(author.isTextual()&&author.asText().startsWith("${secret:")&&exec.isTextual())values.add(exec.asText());
    }
    static boolean hasSavepoint(JsonNode node) {
        if(node==null)return false;
        if(node.isObject()) {
            JsonNode sp=node.path("latestSavepoint");
            if(sp.isObject()&&"COMPLETED".equals(sp.path("status").asText()))return true;
            for(JsonNode child:node)if(hasSavepoint(child))return true;
        }else if(node.isArray())for(JsonNode child:node)if(hasSavepoint(child))return true;
        return false;
    }
    static String status(String engineStatus) {return switch(engineStatus) {case "CREATED","SCHEDULED","PENDING"->"PENDING";case "RUNNING"->"RUNNING";case "DOING_SAVEPOINT"->"SAVING";case "SAVEPOINT_DONE"->"SAVED";case "FINISHED"->"SUCCEEDED";case "FAILED"->"FAILED";case "CANCELED","CANCELLED"->"CANCELED";case "CANCELING"->"CANCELING";default->"UNKNOWN";};}
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void reconcile(){for(ObjectNode r:repo.active())repo.event(r.path("id").asText(),"RECONCILING","平台服务启动，按已保存外部 jobId 恢复状态轮询，不重复提交");}
    @Scheduled(fixedDelayString="${data-sync.seatunnel.poll-delay:2s}")
    public synchronized void poll() {
        List<ObjectNode> active=repo.active();if(active.isEmpty())return;
        JsonNode resources=null;try{resources=engine.labResources();}catch(Exception ignored) { }
        for(ObjectNode r:active) {
            String id=r.path("id").asText(), previous=r.path("status").asText();
            ObjectNode obs=(ObjectNode)r.path("observation").deepCopy();
            try {
                JsonNode exec=LabJson.parse(crypto.decrypt(repo.cipher(id)));List<String> values=executionSecrets(r,exec);
                JsonNode info=engine.labJobInfo(r.path("externalJobId").asText());
                if(info==null||!info.has("jobStatus")) {
                    // A server-side parser rejection is a failed submission even when Zeta uses HTTP 500.
                    // The persisted response also repairs a restart between recording the rejection and its state update.
                    var rejection=repo.events(id).stream().filter(event->event.path("type").asText().equals("SUBMIT_ERROR"))
                        .map(event->event.path("message").asText()).filter(message->{int start=message.indexOf('{');return start>=0&&definiteRejection(message.substring(start));}).findFirst();
                    if(rejection.isPresent()) {obs.put("lastError",rejection.get()).put("finishedAt",Instant.now().toString());repo.observe(id,"FAILED",obs);repo.event(id,"FAILED","引擎明确拒绝提交，且未创建作业");continue;}
                    throw new IllegalStateException("引擎没有返回作业状态");
                }
                String state=status(info.path("jobStatus").asText());
                if(r.has("restoredFrom") && !obs.path("engineExecutionObserved").asBoolean() && state.equals("SAVED")) {
                    // The same external ID can briefly still expose the previous SAVEPOINT_DONE.
                    obs.put("lastPolledAt",Instant.now().toString());repo.observe(id,"PENDING",obs);continue;
                }
                if(!state.equals("UNKNOWN")&&!state.equals("SAVED"))obs.put("engineExecutionObserved",true);
                // REST stop returns before the async engine transition. Keep the pending intent visible.
                if((previous.equals("SAVING")||previous.equals("CANCELING"))&&(state.equals("RUNNING")||state.equals("PENDING")))state=previous;
                obs.set("job",LabJson.redact(info,values));obs.put("lastPolledAt",Instant.now().toString());obs.remove("lastError");
                JsonNode checkpoint=null;try{checkpoint=engine.labCheckpoints(r.path("externalJobId").asText());}catch(Exception ignored){ }
                obs.set("checkpoints",LabJson.redact(checkpoint,values));obs.put("savepointAvailable",hasSavepoint(checkpoint));
                if(!LabRepository.ACTIVE.contains(state))obs.put("finishedAt",Instant.now().toString());
                repo.observe(id,state,obs);
                ObjectNode sample=LabJson.object().put("time",Instant.now().toString()).put("resourceScope","Zeta 引擎进程共享资源，不是单任务独占资源");
                sample.set("metrics",LabJson.redact(info.get("metrics"),values));sample.set("resources",LabJson.redact(resources,values));repo.sample(id,sample);
                if(!state.equals(previous))repo.event(id,state,state.equals("SAVED")?"保存点停止完成；当前数据可能只同步了一部分":"引擎状态："+info.path("jobStatus").asText());
            }catch(Exception e){
                if(!obs.has("lastError"))repo.event(id,"POLL_ERROR","引擎状态暂不可确认，继续保留运行和外部 jobId");
                obs.put("lastError","引擎状态暂不可确认");repo.observe(id,previous,obs);
            }
        }
    }
    public ObjectNode cluster() {
        ObjectNode o=catalog.localFiles();o.put("expectedVersion","2.3.13");o.put("resourceScope","引擎进程共享；缺失数据不可用，不填零");
        try{o.set("overview",LabJson.redact(engine.overview(),List.of()));o.set("resources",LabJson.redact(engine.labResources(),List.of()));}catch(Exception e){o.put("error","运行信息不可用");}
        return o;
    }
    public ObjectNode logs(String id) {
        ObjectNode r=repo.run(id), result=LabJson.object();String jobId=r.path("externalJobId").asText();
        var lines=result.putArray("lines");List<String> values=executionSecrets(r,LabJson.parse(crypto.decrypt(repo.cipher(id))));
        // Read a bounded tail. Only lines tagged with this job ID are attributable to this run.
        Path path=catalog.home.resolve("logs/seatunnel-engine-server.log");
        try(var file=new java.io.RandomAccessFile(path.toFile(),"r")) {
            long start=Math.max(0,file.length()-2_000_000);file.seek(start);byte[] bytes=new byte[(int)(file.length()-start)];file.readFully(bytes);
            List<String> selected=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).lines().filter(line->line.contains(jobId)).toList();
            selected.stream().skip(Math.max(0,selected.size()-500)).forEach(line->lines.add(LabJson.scrub(line,values)));
            result.put("source","本地日志最近 2 MB 中带此 jobId 的行，最多 500 行；恢复沿用 jobId，可能包含前一次运行");
        }catch(Exception e){result.put("error","本地日志不可用");}
        return result;
    }
    public List<ObjectNode> compare(List<String> ids) {
        if(ids.size()<2||ids.size()>4||new HashSet<>(ids).size()!=ids.size())throw new RequestValidationException("请选择 2–4 个不同运行");
        return ids.stream().map(id->{ObjectNode r=detail(id);r.set("samples",LabJson.JSON.valueToTree(repo.samples(id)));return r;}).toList();
    }
}
