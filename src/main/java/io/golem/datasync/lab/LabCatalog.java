package io.golem.datasync.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class LabCatalog {
    private final ObjectNode catalog;
    final Path home;
    public LabCatalog(@Value("${data-sync.lab.installation-path:.runtime/apache-seatunnel-2.3.13}") String home) throws Exception {
        this.home=Path.of(home).toAbsolutePath().normalize();
        try(var input=new ClassPathResource("lab/catalog-2.3.13.json").getInputStream()) { catalog=(ObjectNode)LabJson.JSON.readTree(input); }
    }
    public ObjectNode document() {
        ObjectNode result=catalog.deepCopy();
        result.withArray("groups").forEach(g->{
            ObjectNode group=(ObjectNode)g;
            String id=g.path("id").asText();
            group.put("installed",installed(id));group.put("documented",true);group.put("platformVerified",false);
            group.put("verificationNote","安装与参数定义已核对；组合行为须查看真实运行记录");
            group.put("documentation",docUrl(id));
            group.put("requirements",id.matches("transform.(LLM|Embedding)") ? "需要提供模型服务、加密 API 密钥及网络连接；本机没有托管这些服务" : id.equals("transform.DynamicCompile") ? "需要匹配的 JDK/编译器、脚本语言运行时和用户代码；实际依赖由引擎检查" : "");
            for(JsonNode item:g.path("options")) {
                ObjectNode option=(ObjectNode)item;
                option.put("sourceUrl",sourceUrl(option.path("origin").asText()));option.put("scope",id);option.put("version","2.3.13");option.put("defaultSource","插件定义默认值，并非引擎返回的生效值");
                boolean required=false; var conditions=LabJson.JSON.createArrayNode();
                for(JsonNode rule:g.path("rules")) for(JsonNode key:rule.path("keys")) if(key.asText().equals(item.path("key").asText())) {
                    if(rule.path("kind").asText().equals("AbsolutelyRequiredOptions")) required=true;
                    else conditions.add(rule);
                }
                option.put("required",required);option.set("conditions",conditions);
            }
        });return result;
    }
    public JsonNode group(String id) { for(JsonNode g:catalog.path("groups")) if(g.path("id").asText().equals(id))return g; return LabJson.object(); }
    public Map<String,JsonNode> options(String id) {
        Map<String,JsonNode> result=new LinkedHashMap<>();
        String common=id.contains(".") ? id.substring(0,id.indexOf('.'))+".common" : "";
        for(JsonNode o:group(common).path("options")) result.put(o.path("key").asText(),o);
        for(JsonNode o:group(id).path("options")) result.put(o.path("key").asText(),o);
        return result;
    }
    public boolean installed(String id) {
        if(id.startsWith("source.Jdbc")||id.equals("sink.Jdbc"))return Files.isRegularFile(home.resolve("connectors/connector-jdbc-2.3.13.jar")) && hasMysqlDriver();
        if(id.startsWith("transform.")&&!id.endsWith("common")) {
            try(var jar=new JarFile(home.resolve("lib/seatunnel-transforms-v2.jar").toFile())) { return jar.getJarEntry(group(id).path("origin").asText().replace('.','/')+".class")!=null; }
            catch(Exception e) {return false;}
        }
        return Files.isRegularFile(home.resolve("starter/seatunnel-starter.jar"));
    }
    private boolean hasMysqlDriver() { try(var files=Files.list(home.resolve("lib"))) {return files.anyMatch(p->p.getFileName().toString().startsWith("mysql-connector"));}catch(Exception e){return false;} }
    static String sourceUrl(String origin) {
        String module=origin.startsWith("org.apache.seatunnel.connectors")?"seatunnel-connectors-v2/connector-jdbc":origin.startsWith("org.apache.seatunnel.transform")?"seatunnel-transforms-v2":"seatunnel-api";
        return "https://github.com/apache/seatunnel/blob/2.3.13/"+module+"/src/main/java/"+origin.split("\\$")[0].replace('.','/')+".java";
    }
    static String docUrl(String id) {
        String path=id.equals("env")?"introduction/configuration/env":id.startsWith("source.Jdbc")?"connectors/source/Jdbc":id.equals("sink.Jdbc")?"connectors/sink/Jdbc":id.startsWith("transform.")&&!id.endsWith("common")?"transforms/"+id.split("\\.")[1].toLowerCase(Locale.ROOT):"connectors/common-options";
        return "https://seatunnel.apache.org/docs/2.3.13/"+path+"/";
    }
    public boolean savepointFilesPresent(String jobId,JsonNode checkpoints) {
        if(!jobId.matches("[0-9]+"))return false;
        try {
            String yaml=Files.readString(home.resolve("config/seatunnel.yaml"));
            if(!java.util.regex.Pattern.compile("(?m)^\\s*fs\\.defaultFS:\\s*file:").matcher(yaml).find())return false;
            var namespace=java.util.regex.Pattern.compile("(?m)^\\s*namespace:\\s*([^#\\r\\n]+)").matcher(yaml);
            if(!namespace.find())return false;
            Path directory=Path.of(namespace.group(1).trim().replace("\"", "").replace("'", "")).resolve(jobId);
            if(!Files.isDirectory(directory))return false;
            JsonNode pipelines=checkpoints.path("pipelines");if(!pipelines.isArray()||pipelines.isEmpty())return false;
            for(JsonNode pipeline:pipelines) {
                JsonNode sp=pipeline.path("latestSavepoint");
                if(!sp.path("status").asText().equals("COMPLETED"))return false;
                String suffix="-"+pipeline.path("pipelineId").asText()+"-"+sp.path("checkpointId").asText()+".ser";
                try(var files=Files.list(directory)) {if(files.noneMatch(p->p.getFileName().toString().endsWith(suffix)&&Files.isRegularFile(p)))return false;}
            }
            return true;
        }catch(Exception e){return false;}
    }
    public ObjectNode localFiles() {
        ObjectNode result=LabJson.object(); var files=result.putArray("files");
        for(String name:List.of("seatunnel.yaml","hazelcast.yaml","jvm_options")) {
            ObjectNode file=files.addObject().put("name",name).put("restartRequired",true).put("source","本地磁盘文件，不代表运行进程已加载");
            try {file.put("content",LabJson.scrub(Files.readString(home.resolve("config").resolve(name)),List.of()).replaceAll("(?im)^(.*(?:password|secret|token|api.key).*[:=]).*$","$1 ******"));}
            catch(Exception e){file.putNull("content");file.put("error","文件不可用");}
        }
        var plugins=result.putArray("plugins");
        for(String dir:List.of("connectors","lib","starter"))try(var paths=Files.list(home.resolve(dir))) {
            paths.filter(p->p.toString().endsWith(".jar")).sorted().forEach(p->plugins.addObject().put("file",dir+"/"+p.getFileName()).put("versionSource","文件名/本机安装包；版本以引擎 overview 为准"));
        }catch(Exception ignored) { }
        return result;
    }
}
