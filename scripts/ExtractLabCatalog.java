import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.util.*;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.ObjectMapper;

/** Run in an isolated JVM against the pinned SeaTunnel installation, never the API classloader. */
public class ExtractLabCatalog {
  static final Map<String,Object> groups = new LinkedHashMap<>();
  static String typeName(Type t) {
    if(t instanceof ParameterizedType p) return typeName(p.getRawType())+"<"+String.join(",",Arrays.stream(p.getActualTypeArguments()).map(ExtractLabCatalog::typeName).toList())+">";
    return t.getTypeName();
  }
  static Map<String,Object> option(Option<?> o, String origin) {
    var m=new LinkedHashMap<String,Object>();
    m.put("key",o.key());m.put("javaType",typeName(o.typeReference().getType()));
    String t=typeName(o.typeReference().getType());
    m.put("type",t.contains("List")?"array":t.contains("Map")?"object":t.contains("Boolean")?"boolean":t.matches(".*(Integer|Long|Double|Float|BigDecimal).* ".trim())?"number":"string");
    m.put("defaultValue",o.defaultValue());m.put("description",o.getDescription());m.put("fallbackKeys",o.getFallbackKeys());m.put("origin",origin);
    if(o.typeReference().getType() instanceof Class<?> c && c.isEnum())m.put("enumValues",Arrays.stream(c.getEnumConstants()).map(Object::toString).toList());
    return m;
  }
  static Map<String,Object> group(String id,String className,String plugin) throws Exception {
    var m=new LinkedHashMap<String,Object>();m.put("id",id);m.put("plugin",plugin);m.put("version","2.3.13");m.put("origin",className);
    var options=new TreeMap<String,Map<String,Object>>();var rules=new ArrayList<Map<String,Object>>();
    Class<?> c=Class.forName(className);
    if(Factory.class.isAssignableFrom(c)){
      Factory f=(Factory)c.getDeclaredConstructor().newInstance();m.put("plugin",f.factoryIdentifier());
      var rule=f.optionRule();
      for(var o:rule.getOptionalOptions())options.put(o.key(),option(o,className));
      for(var required:rule.getRequiredOptions()){
        var r=new LinkedHashMap<String,Object>();r.put("kind",required.getClass().getSimpleName());r.put("keys",required.getOptions().stream().map(Option::key).toList());
        if(required instanceof RequiredOption.ConditionalRequiredOptions condition)r.put("condition",condition.getExpression().toString());
        rules.add(r);
        for(var o:required.getOptions())options.put(o.key(),option(o,className));
      }
    }else for(var f:c.getFields())if(Modifier.isStatic(f.getModifiers())&&Option.class.isAssignableFrom(f.getType())){
      Option<?> o=(Option<?>)f.get(null);options.put(o.key(),option(o,f.getDeclaringClass().getName()));
    }
    m.put("options",new ArrayList<>(options.values()));m.put("rules",rules);groups.put(id,m);return m;
  }
  public static void main(String[] a)throws Exception {
    group("env","org.apache.seatunnel.api.options.EnvCommonOptions","");
    group("source.common","org.apache.seatunnel.api.options.SourceConnectorCommonOptions","");
    group("sink.common","org.apache.seatunnel.api.options.SinkConnectorCommonOptions","");
    group("transform.common","org.apache.seatunnel.api.options.ConnectorCommonOptions","");
    for(String side:List.of("Source","Sink")){
      var g=group(side.toLowerCase()+".Jdbc","org.apache.seatunnel.connectors.seatunnel.jdbc."+side.toLowerCase()+".Jdbc"+side+"Factory","Jdbc");
      var all=group("jdbc."+side.toLowerCase()+".options","org.apache.seatunnel.connectors.seatunnel.jdbc.config.Jdbc"+side+"Options","Jdbc");
      var options=new TreeMap<String,Object>();
      for(var x:(List<Map<String,Object>>)all.get("options"))options.put((String)x.get("key"),x);
      for(var x:(List<Map<String,Object>>)g.get("options"))options.put((String)x.get("key"),x);
      g.put("options",new ArrayList<>(options.values()));groups.remove("jdbc."+side.toLowerCase()+".options");
    }
    try(var jar=new JarFile(a[0]+"/lib/seatunnel-transforms-v2.jar")){
      String factories=new String(jar.getInputStream(jar.getJarEntry("META-INF/services/org.apache.seatunnel.api.table.factory.Factory")).readAllBytes());
      for(String line:factories.lines().filter(x->!x.isBlank()&&!x.startsWith("#")).toList()){
        var f=(Factory)Class.forName(line).getDeclaredConstructor().newInstance();group("transform."+f.factoryIdentifier(),line,f.factoryIdentifier());
      }
    }
    var nested=new LinkedHashMap<String,Object>();nested.put("id","source.Jdbc.table_list[]");nested.put("plugin","Jdbc");nested.put("version","2.3.13");nested.put("origin","org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcSourceTableConfig");
    var nestedOptions=new ArrayList<Map<String,Object>>();
    for(var field:Class.forName((String)nested.get("origin")).getDeclaredFields()) {
      var annotation=field.getAnnotation(org.apache.seatunnel.shade.com.fasterxml.jackson.annotation.JsonProperty.class);
      if(annotation==null)continue;
      String key=annotation.value();
      var sourceOptions=(List<Map<String,Object>>)((Map<String,Object>)groups.get("source.Jdbc")).get("options");
      sourceOptions.stream().filter(o->key.equals(o.get("key"))).findFirst().ifPresent(o->{var copy=new LinkedHashMap<>(o);copy.put("origin",nested.get("origin"));copy.put("defaultValue",null);copy.put("condition","Inherits the top-level option when omitted; JdbcSourceTableConfig.of");nestedOptions.add(copy);});
    }
    nested.put("options",nestedOptions);nested.put("rules",List.of());groups.put((String)nested.get("id"),nested);
    for(Object original:new ArrayList<>(groups.values())) {
      var parent=(Map<String,Object>)original;
      for(var option:(List<Map<String,Object>>)parent.get("options")) {
        String javaType=(String)option.get("javaType");
        var matcher=java.util.regex.Pattern.compile("org\\.apache\\.seatunnel\\.[A-Za-z0-9_.$]+").matcher(javaType);
        while(matcher.find()) {
          Class<?> type=Class.forName(matcher.group());
          if(type.isEnum()||type.isInterface()||type.getName().endsWith("JdbcSourceTableConfig"))continue;
          var fields=new ArrayList<Map<String,Object>>();
          for(var field:type.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())||field.isSynthetic())continue;
            var annotation=field.getAnnotation(org.apache.seatunnel.shade.com.fasterxml.jackson.annotation.JsonProperty.class);
            var child=new LinkedHashMap<String,Object>();String ft=typeName(field.getGenericType());
            child.put("key",annotation==null?field.getName():annotation.value());child.put("javaType",ft);child.put("type",ft.contains("List")?"array":ft.contains("Map")?"object":ft.contains("Boolean")||ft.equals("boolean")?"boolean":ft.matches(".*(Integer|Long|Double|Float|BigDecimal).*" )?"number":"string");child.put("defaultValue",null);child.put("description","Nested configuration field; default/required behavior is determined by the parent plugin");child.put("origin",type.getName());var alias=field.getAnnotation(org.apache.seatunnel.shade.com.fasterxml.jackson.annotation.JsonAlias.class);child.put("fallbackKeys",alias==null?List.of():Arrays.asList(alias.value()));
            try {var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object instance=constructor.newInstance();field.setAccessible(true);child.put("defaultValue",field.get(instance));}catch(Exception ignored) { }
            fields.add(child);
          }
          if(fields.isEmpty())continue;
          var childGroup=new LinkedHashMap<String,Object>();String childId=parent.get("id")+"."+option.get("key")+(javaType.startsWith("java.util.List")?"[]":"");
          childGroup.put("id",childId);childGroup.put("plugin",parent.get("plugin"));childGroup.put("version","2.3.13");childGroup.put("origin",type.getName());childGroup.put("options",fields);childGroup.put("rules",List.of());childGroup.put("nested",true);groups.put(childId,childGroup);
        }
      }
      if(((String)parent.get("id")).startsWith("transform.") && ((List<Map<String,Object>>)parent.get("options")).stream().anyMatch(o->o.get("key").equals("table_transform"))) {
        var child=new LinkedHashMap<>(parent);String childId=parent.get("id")+".table_transform[]";child.put("id",childId);child.put("nested",true);child.put("options",((List<Map<String,Object>>)parent.get("options")).stream().filter(o->!o.get("key").equals("table_transform")).toList());groups.put(childId,child);
      }
    }
    var commonRoots=Set.of("plugin_name","plugin_input","plugin_output","parallelism","schema","schema.fields","catalog","tables_configs","dag-parsing.mode","multi_table_sink_replica");
    for(String common:List.of("source.common","transform.common","sink.common")) {
      var parent=(Map<String,Object>)groups.get(common);var retained=new ArrayList<Map<String,Object>>();
      for(var option:(List<Map<String,Object>>)parent.get("options")) {
        if(commonRoots.contains(option.get("key")))retained.add(option);
        else {
          String origin=(String)option.get("origin"),id="metadata."+origin.substring(origin.lastIndexOf('.')+1);
          var group=(Map<String,Object>)groups.computeIfAbsent(id,k->{var m=new LinkedHashMap<String,Object>();m.put("id",id);m.put("plugin","");m.put("version","2.3.13");m.put("origin",origin);m.put("options",new ArrayList<Map<String,Object>>());m.put("rules",List.of());return m;});
          var entries=(List<Map<String,Object>>)group.get("options");
          if(entries.stream().noneMatch(o->o.get("key").equals(option.get("key")))) {var entry=new LinkedHashMap<>(option);entry.put("condition","Common schema/catalog metadata definition; placement is determined by the plugin parser, see pinned source");entries.add(entry);}
        }
      }
      var parallel=new LinkedHashMap<>((Map<String,Object>)((List<Map<String,Object>>)((Map<String,Object>)groups.get("env")).get("options")).stream().filter(o->o.get("key").equals("parallelism")).findFirst().orElseThrow());parallel.put("defaultValue",null);parallel.put("condition","Overrides env.parallelism when explicitly configured on this node");retained.add(parallel);parent.put("options",retained);
    }
    var root=new LinkedHashMap<String,Object>();root.put("version","2.3.13");root.put("provenance","Extracted from installed SeaTunnel Options and Factory.optionRule() in an isolated JVM");root.put("groups",groups.values());
    Files.writeString(Path.of(a[1]),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root));
  }
}
