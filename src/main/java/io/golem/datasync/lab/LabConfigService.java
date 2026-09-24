package io.golem.datasync.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.service.*;
import io.golem.datasync.security.CryptoService;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LabConfigService {
    private final LabCatalog catalog;
    private final DataSourceService sources;
    private final MysqlMetadataService metadata;
    private final CryptoService crypto;
    private static final Set<String> MANAGED=Set.of("url","username","user","password","driver","database","base-url");
    private static final Pattern SECRET=Pattern.compile("\\$\\{secret:([A-Za-z0-9_-]{1,120})}");
    public record Prepared(ObjectNode authoring,ObjectNode execution,ObjectNode report,List<String> secrets) { }
    public LabConfigService(LabCatalog catalog,DataSourceService sources,MysqlMetadataService metadata,CryptoService crypto) {this.catalog=catalog;this.sources=sources;this.metadata=metadata;this.crypto=crypto;}
    public Prepared prepare(JsonNode input,JsonNode bindings,Map<String,String> secrets,boolean preflight) {
        if(!input.isObject()||!bindings.isObject()) throw new RequestValidationException("config 和 bindings 必须是 JSON 对象");
        if(input.toString().length()>1_000_000) throw new RequestValidationException("配置不能超过 1 MB");
        ObjectNode author=input.deepCopy(), exec=input.deepCopy(), report=LabJson.object();
        ArrayNode errors=report.putArray("errors"), warnings=report.putArray("warnings"), tables=report.putArray("tables"), schemas=report.putArray("sourceSchemas");
        report.put("validationLevel",preflight?"平台静态校验 + 只读连接/元数据预检；转换后结构及插件行为由真实引擎确认":"平台静态校验；尚未进行引擎执行校验");
        List<String> values=new ArrayList<>(secrets.values());
        if(!input.path("env").isObject()) errors.add("env 必须是对象");
        if(!"BATCH".equals(input.path("env").path("job.mode").asText("BATCH")))errors.add("本期仅支持 BATCH");
        validateOptions(input.path("env"),"env","env",errors,warnings);
        var outputs=new LinkedHashMap<String,String>();var edges=new LinkedHashMap<String,List<String>>();
        Set<String> nodes=new HashSet<>();
        for(String section:List.of("source","transform","sink")) {
            JsonNode array=input.path(section);
            if(!array.isArray()) {errors.add(section+" 必须是数组");continue;}
            if(!section.equals("transform") && array.isEmpty())errors.add(section+" 至少一个节点");
            for(int i=0;i<array.size();i++) {
                String nodeKey=section+"/"+i;nodes.add(nodeKey);
                if(!array.get(i).isObject()) {errors.add(nodeKey+" 必须是对象");continue;}
                ObjectNode node=(ObjectNode)exec.path(section).get(i), draft=(ObjectNode)author.path(section).get(i);
                String plugin=node.path("plugin_name").asText(), group=section+"."+plugin;
                if(plugin.contains("."))errors.add(nodeKey+" plugin_name 不能是嵌套参数作用域");
                if((!section.equals("transform")&&!plugin.equals("Jdbc"))||catalog.group(group).isEmpty())errors.add(nodeKey+" 插件不在本期目录范围内");
                else if(!catalog.installed(group))errors.add(nodeKey+" 缺少本机插件或 JDBC 驱动");
                if(plugin.equals("Jdbc")) {
                    String id=bindings.path(nodeKey).asText();
                    if(id.isEmpty())errors.add(nodeKey+" 必须绑定数据源");
                    else try {
                        var ds=sources.require(id);String pw=crypto.decrypt(ds.encryptedPassword);values.add(pw);
                        for(String field:MANAGED) if(node.has(field)&&!node.path(field).asText().equals("${datasource:"+nodeKey+":"+field+"}")) errors.add(nodeKey+"."+field+" 是托管字段，请通过数据源绑定配置");
                        node.remove(List.of("user","base-url"));draft.remove(List.of("user","base-url"));
                        node.put("url",metadata.jdbcUrl(ds));node.put("username",ds.username);node.put("password",pw);node.put("driver","com.mysql.cj.jdbc.Driver");
                        if(section.equals("sink"))node.put("database",ds.databaseName);
                        for(String field:List.of("url","username","password","driver"))draft.put(field,"${datasource:"+nodeKey+":"+field+"}");
                        if(section.equals("sink"))draft.put("database","${datasource:"+nodeKey+":database}");
                        if(node.path("properties").isObject())for(String field:List.of("password","user","username","url"))if(node.path("properties").has(field))errors.add(nodeKey+".properties 不可覆盖托管连接字段 "+field);
                        if(preflight) {
                            try(var connection=metadata.open(ds)) { if(!connection.isValid(5))errors.add(nodeKey+" 连接不可用"); }
                            if(section.equals("source")) {
                                var listed=node.has("table_list") ? node.path("table_list") : LabJson.JSON.createArrayNode().add(node);
                                for(JsonNode table:listed) {
                                    String path=table.path("table_path").asText();
                                    if(!path.isEmpty()&&!table.path("use_regex").asBoolean(node.path("use_regex").asBoolean())) {
                                        String simple=path.startsWith(ds.databaseName+".")?path.substring(ds.databaseName.length()+1):path;
                                        if(!simple.contains(".")) {
                                            var columns=metadata.listColumns(ds,simple);
                                            if(columns.isEmpty())errors.add(nodeKey+" 源表不存在或没有可见字段："+path);
                                            schemas.addObject().put("node",nodeKey).put("table",path).set("columns",LabJson.JSON.valueToTree(columns));
                                        }
                                    }
                                }
                            }
                        }
                    }catch(Exception e) { errors.add(nodeKey+" 数据源连接或元数据预检失败："+LabJson.scrub(e.getMessage(),values)); }
                    if(section.equals("sink")&&node.path("is_exactly_once").asBoolean())warnings.add(nodeKey+"：MySQL XA 恢复需要 XA_RECOVER_ADMIN 权限；驱动与数据库版本、事务超时必须匹配。启用参数不等于已验证 exactly-once。");
                    if(section.equals("source")) {
                        if(node.has("table_list")&&(node.has("table_path")||node.has("query")))errors.add(nodeKey+" table_list 与 table_path/query 互斥");
                        if(!node.has("table_list")&&!node.has("query")&&!node.has("table_path"))errors.add(nodeKey+" 需要 table_list、table_path 或 query");
                        if(node.has("table_list")) {
                            if(!node.path("table_list").isArray()||node.path("table_list").isEmpty())errors.add(nodeKey+" table_list 必须是非空数组");
                            else for(JsonNode table:node.path("table_list")) {
                                validateOptions(table,"source.Jdbc.table_list[]",nodeKey+".table_list[]",errors,warnings);
                                if(!table.has("table_path")&&!table.has("query"))errors.add(nodeKey+" table_list 项需要 table_path 或 query");
                            }
                        }
                        warnings.add(nodeKey+"：table_path 模式用 split.*；query 模式用 partition_*。无可分片键时并行度未必提高读取并发。");
                    }
                    tables.addObject().put("node",nodeKey).put("role",section).set("configuration",LabJson.redact(node,values));
                }
                validateOptions(node,group,nodeKey,errors,warnings);
                String output=node.path("plugin_output").asText();
                if(!section.equals("sink")&&output.isBlank())errors.add(nodeKey+" 需要 plugin_output");
                if(!output.isBlank()&&outputs.putIfAbsent(output,nodeKey)!=null)errors.add("重复输出名："+output);
                List<String> inputs=new ArrayList<>();JsonNode in=node.path("plugin_input");
                if(in.isTextual())inputs.add(in.asText());else if(in.isArray())in.forEach(v->inputs.add(v.asText()));
                if(!section.equals("source")&&inputs.isEmpty())errors.add(nodeKey+" 需要 plugin_input");
                edges.put(nodeKey,inputs);
                if(plugin.equals("Sql")) {
                    if(!node.has("query"))errors.add(nodeKey+" Sql 需要 query");
                    warnings.add(nodeKey+"：SQL Transform 处理内存中的单行，不支持多源 JOIN/聚合；JDBC Source query 则在 MySQL 执行。表达式须由引擎验证。");
                }
                if(plugin.equals("LLM")||plugin.equals("Embedding")||plugin.equals("DynamicCompile"))warnings.add(nodeKey+" 需要额外运行条件，请查看插件目录");
            }
        }
        bindings.fieldNames().forEachRemaining(key->{if(!nodes.contains(key)||key.startsWith("transform/"))errors.add("无效数据源绑定："+key);});
        for(var entry:edges.entrySet())for(String inputName:entry.getValue())if(!outputs.containsKey(inputName))errors.add(entry.getKey()+" 引用了不存在的输出："+inputName);
        Set<String> visiting=new HashSet<>(), done=new HashSet<>();
        for(String node:nodes)if(cycle(node,edges,outputs,visiting,done)){errors.add("节点引用存在环路");break;}
        checkSecrets(author,"config",errors);
        exec=(ObjectNode)resolve(exec,secrets,errors);
        warnings.add("数据库预检不执行 SQL。转换后的目标字段兼容性、SQL 表达式、写入与建表策略由真实运行验证；不套用单表字段原样匹配规则。");
        report.put("valid",errors.isEmpty());report.set("preview",LabJson.redact(exec,values));report.set("config",author);
        report.set("dag",LabJson.JSON.valueToTree(edges));
        return new Prepared(author,exec,report,values);
    }
    private boolean cycle(String node,Map<String,List<String>> edges,Map<String,String> outputs,Set<String> visiting,Set<String> done) {
        if(done.contains(node))return false;if(!visiting.add(node))return true;
        for(String ref:edges.getOrDefault(node,List.of()))if(outputs.containsKey(ref)&&cycle(outputs.get(ref),edges,outputs,visiting,done))return true;
        visiting.remove(node);done.add(node);return false;
    }
    void checkSecrets(JsonNode node,String path,ArrayNode errors) {
        if(node.isObject())node.fields().forEachRemaining(e->{
            JsonNode v=e.getValue();
            if(LabJson.sensitive(e.getKey())&&!v.isNull()&&!(v.isTextual()&&(SECRET.matcher(v.asText()).matches()||v.asText().startsWith("${datasource:"))))errors.add(path+"."+e.getKey()+" 必须使用加密秘密引用 ${secret:name}");
            checkSecrets(v,path+"."+e.getKey(),errors);
        });
        else if(node.isArray())node.forEach(v->checkSecrets(v,path+"[]",errors));
        else if(node.isTextual()&&node.asText().matches("(?is).*(?:password|passwd|pwd|secret|api_key|token)\\s*[=:]\\s*[^\\s]+.*")&&!SECRET.matcher(node.asText()).matches())errors.add(path+" 可能内嵌敏感值，请使用独立秘密引用");
    }
    private JsonNode resolve(JsonNode node,Map<String,String> secrets,ArrayNode errors) {
        if(node.isObject()) {var r=LabJson.object();node.fields().forEachRemaining(e->r.set(e.getKey(),resolve(e.getValue(),secrets,errors)));return r;}
        if(node.isArray()){var r=LabJson.JSON.createArrayNode();node.forEach(v->r.add(resolve(v,secrets,errors)));return r;}
        if(node.isTextual()) {var m=SECRET.matcher(node.asText());if(m.matches()){String secret=secrets.get(m.group(1));if(secret==null){errors.add("秘密引用未配置："+m.group(1));return node;}return TextNode.valueOf(secret);} }
        return node;
    }
    void validateOptions(JsonNode node,String group,String path,ArrayNode errors,ArrayNode warnings) {
        if(!node.isObject()){errors.add(path+" 必须是对象");return;}
        Map<String,JsonNode> options=catalog.options(group);Map<String,JsonNode> effective=new HashMap<>();
        options.forEach((key,opt)->{if(opt.hasNonNull("defaultValue"))effective.put(key,opt.get("defaultValue"));});
        node.fields().forEachRemaining(e->{
            String key=e.getKey();JsonNode opt=options.get(key),value=e.getValue();
            if(opt==null) for(JsonNode candidate:options.values())for(JsonNode alias:candidate.path("fallbackKeys"))if(alias.asText().equals(key))opt=candidate;
            if(opt==null){warnings.add(path+"."+key+" 未验证，参数原样保留");return;}
            effective.put(opt.path("key").asText(),value);
            String nestedId=group+"."+key+(value.isArray()?"[]":"");
            if(!catalog.group(nestedId).isEmpty()) {
                if(value.isArray()) for(JsonNode child:value)validateOptions(child,nestedId,path+"."+key+"[]",errors,warnings);
                else if(value.isObject())validateOptions(value,nestedId,path+"."+key,errors,warnings);
            }
            if(value.isNull()){errors.add(path+"."+key+" 不可为 null");return;}
            boolean valid=switch(opt.path("type").asText()) {case "number"->value.isNumber();case "boolean"->value.isBoolean();case "object"->value.isObject();case "array"->value.isArray()||(key.equals("plugin_input")&&value.isTextual());default->value.isTextual();};
            if(!valid)errors.add(path+"."+key+" 类型应为 "+opt.path("type").asText());
            if(opt.has("enumValues")&&!opt.path("enumValues").toString().contains("\""+value.asText()+"\""))errors.add(path+"."+key+" 不在枚举范围内");
            if(opt.path("javaType").asText().matches(".*(Integer|Long).*" )&&value.isNumber()&&!value.isIntegralNumber())errors.add(path+"."+key+" 必须为整数");
        });
        for(JsonNode rule:catalog.group(group).path("rules")) {
            String kind=rule.path("kind").asText();List<String> keys=new ArrayList<>();rule.path("keys").forEach(k->keys.add(k.asText()));
            long present=keys.stream().filter(k->effective.containsKey(k)&&!effective.get(k).isNull()).count();
            boolean required=kind.equals("AbsolutelyRequiredOptions") || kind.equals("BundledRequiredOptions")&&present>0 || kind.equals("ConditionalRequiredOptions")&&condition(rule.path("condition").asText(),effective);
            if(required&&present<keys.size())errors.add(path+" 缺少关联必填参数："+String.join(", ",keys));
            if(kind.equals("ExclusiveRequiredOptions")&&present!=1)errors.add(path+" 以下参数必须且只能设置一个："+String.join(", ",keys));
        }
        for(String key:List.of("parallelism","batch_size","partition_num","split.size","checkpoint.interval","checkpoint.timeout"))if(node.has(key)&&node.path(key).isNumber()&&node.path(key).asDouble()<=0)errors.add(path+"."+key+" 必须大于 0");
    }
    private boolean condition(String expression,Map<String,JsonNode> effective) {
        for(String or:expression.split(" \\|\\| ")) {
            boolean match=true;
            for(String and:or.split(" && ")) {var m=Pattern.compile("'([^']+)' == (.*)").matcher(and.trim());if(!m.matches()){match=false;break;}match&=effective.containsKey(m.group(1))&&effective.get(m.group(1)).asText().equals(m.group(2));}
            if(match)return true;
        }return false;
    }
}
